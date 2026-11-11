package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.payment.UpiService;
import com.appbazaar.repo.*;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pay in rupees by scanning a UPI QR code. There is no payment gateway, so the buyer types the bank
 * transaction id (UTR) and an admin checks it in the UPI app or bank statement before approving.
 */
@RestController
@RequestMapping("/api/upi")
public class UpiController {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UpiService upi;
    private final UserRepository users;
    private final ProductRepository products;
    private final ManualPaymentRepository payments;
    private final int registrationFee;

    public UpiController(UpiService upi, UserRepository users, ProductRepository products,
                         ManualPaymentRepository payments, @Value("${app.registration-fee}") int registrationFee) {
        this.upi = upi; this.users = users; this.products = products;
        this.payments = payments; this.registrationFee = registrationFee;
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

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("enabled", upi.enabled(), "rate", upi.rate());
    }

    @PostMapping("/start")
    @Transactional
    public UpiStartRes start(@Valid @RequestBody UpiStartReq r, Authentication auth) {
        if (!upi.enabled()) throw new ResponseStatusException(BAD_REQUEST, "UPI payments are not set up");
        User u = users.findByEmail(auth.getName()).orElseThrow();
        boolean unpaid = Boolean.FALSE.equals(u.getFeePaid());
        if (payments.countByUserAndStatus(u, "PENDING") >= 10)
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "You have too many unfinished UPI payments. Finish or wait for them first");
        ManualPayment p = new ManualPayment();
        p.setUser(u);
        p.setStatus("PENDING");
        p.setCreatedAt(Instant.now());
        String note;
        if ("fee".equals(r.kind())) {
            if (!unpaid) throw new ResponseStatusException(CONFLICT, "Registration fee is already paid");
            p.setKind("fee");
            p.setAmountUsd(registrationFee);
            p.setProductIds("");
            note = "AppBazaar fee";
        } else if ("order".equals(r.kind())) {
            if (unpaid) throw new ResponseStatusException(FORBIDDEN, "Pay the registration fee first");
            List<Long> ids = r.productIds() == null ? List.of() : r.productIds().stream().distinct().toList();
            if (ids.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Your cart is empty");
            List<Product> items = products.findAllById(ids);
            if (items.size() != ids.size() || items.stream().anyMatch(x -> x.getStatus() != Status.LIVE))
                throw new ResponseStatusException(BAD_REQUEST, "Some products are no longer available");
            if (items.stream().anyMatch(x -> x.getStoredFileName() == null))
                throw new ResponseStatusException(BAD_REQUEST, "Some products in your cart have no file yet and cannot be bought");
            p.setKind("order");
            p.setAmountUsd(items.stream().mapToInt(Product::getPrice).sum()); // price from the database
            p.setProductIds(String.join(",", ids.stream().map(String::valueOf).toList()));
            note = "AppBazaar order";
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown payment type");
        }
        p.setAmountInr(upi.toInr(p.getAmountUsd()));
        p.setReference(newReference());
        payments.save(p);
        return new UpiStartRes(view(p, products, false), upi.link(p.getAmountInr(), p.getReference(), note),
                upi.id(), upi.payee());
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

    private String newReference() {
        StringBuilder b = new StringBuilder("AB");
        for (int i = 0; i < 8; i++) b.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return b.toString();
    }
}
