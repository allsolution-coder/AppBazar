package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.web.Dto.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The admin checks each reported UPI payment (amount and transaction id) and then approves or rejects it. */
@RestController
@RequestMapping("/api/admin/upi")
public class UpiAdminController {
    private final ManualPaymentRepository payments;
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;

    public UpiAdminController(ManualPaymentRepository payments, ProductRepository products,
                              UserRepository users, OrderRepository orders) {
        this.payments = payments; this.products = products; this.users = users; this.orders = orders;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UpiPaymentRes> waiting() {
        return payments.findByStatusOrderByIdAsc("SUBMITTED").stream()
                .map(p -> UpiController.view(p, products, true)).toList();
    }

    private ManualPayment submitted(Long id) {
        ManualPayment p = payments.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment not found"));
        if (!"SUBMITTED".equals(p.getStatus()))
            throw new ResponseStatusException(CONFLICT, "This payment was already reviewed");
        return p;
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public UpiPaymentRes approve(@PathVariable Long id) {
        ManualPayment p = submitted(id);
        User u = p.getUser();
        if ("fee".equals(p.getKind())) {
            if (Boolean.FALSE.equals(u.getFeePaid())) {
                u.setFeePaid(true);
                u.setFeePaidAt(Instant.now());
                u.setFeePaymentRef("UPI:" + p.getUtr());
                users.save(u);
            }
        } else {
            List<Long> ids = Arrays.stream(p.getProductIds().split(",")).map(Long::valueOf).toList();
            List<Product> items = products.findAllById(ids);
            if (items.size() != ids.size())
                throw new ResponseStatusException(CONFLICT, "A product in this order no longer exists");
            Orders o = new Orders();
            o.setBuyer(u);
            o.setItems(items);
            o.setTotal(p.getAmountUsd());
            o.setPaymentRef("UPI:" + p.getUtr());
            orders.save(o);
        }
        p.setStatus("APPROVED");
        p.setReviewedAt(Instant.now());
        payments.save(p);
        return UpiController.view(p, products, true);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public UpiPaymentRes reject(@PathVariable Long id, @RequestBody(required = false) RejectReq r) {
        ManualPayment p = submitted(id);
        String why = r == null || r.reason() == null ? "" : r.reason().trim();
        p.setNote(why.length() > 200 ? why.substring(0, 200) : why);
        p.setStatus("REJECTED");
        p.setReviewedAt(Instant.now());
        payments.save(p);
        return UpiController.view(p, products, true);
    }
}
