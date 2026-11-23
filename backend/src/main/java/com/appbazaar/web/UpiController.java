package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Completes and lists UPI payments created before the switch to PayPal.
 */
@RestController
@RequestMapping("/api/upi")
public class UpiController {

    private final UserRepository users;
    private final ProductRepository products;
    private final ManualPaymentRepository payments;

    public UpiController(UserRepository users, ProductRepository products, ManualPaymentRepository payments) {
        this.users = users;
        this.products = products;
        this.payments = payments;
    }

    static UpiPaymentRes view(ManualPayment p, ProductRepository products, boolean withUser) {
        List<String> items = List.of();
        if ("order".equals(p.getKind()) && p.getProductIds() != null && !p.getProductIds().isBlank()) {
            List<Long> ids = Arrays.stream(p.getProductIds().split(",")).map(Long::valueOf).toList();
            items = products.findAllById(ids).stream().map(Product::getName).toList();
        }
        return new UpiPaymentRes(p.getId(), p.getKind(), p.getAmountUsd(), p.getAmountInr(), p.getReference(),
                p.getUtr(), p.getStatus(), p.getNote(), p.getCreatedAt(), items,
                withUser ? p.getUser().getEmail() : null);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public UpiPaymentRes submit(@PathVariable Long id, @Valid @RequestBody UtrReq r, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        ManualPayment p = payments.findById(id).filter(x -> x.getUser().getId().equals(u.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment not found"));
        if (!"PENDING".equals(p.getStatus()))
            throw new ResponseStatusException(CONFLICT, "This payment was already submitted");
        String utr = r.utr().trim().toUpperCase();
        if (!utr.matches("[A-Z0-9]{8,30}"))
            throw new ResponseStatusException(BAD_REQUEST, "The transaction ID looks wrong. Copy the UTR or UPI reference number from your payment app");
        if (payments.existsByUtr(utr))
            throw new ResponseStatusException(CONFLICT, "This transaction ID was already used");
        p.setUtr(utr);
        p.setStatus("SUBMITTED");
        p.setSubmittedAt(Instant.now());
        payments.save(p);
        return view(p, products, false);
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<UpiPaymentRes> mine(Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        return payments.findByUserOrderByIdDesc(u).stream().map(p -> view(p, products, false)).toList();
    }

}
