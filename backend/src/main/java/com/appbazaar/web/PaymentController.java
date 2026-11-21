package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.payment.PayPalService;
import com.appbazaar.payment.UpiService;
import com.appbazaar.payment.PayPalService.Item;
import com.appbazaar.repo.*;
import com.appbazaar.security.JwtService;
import com.appbazaar.web.Dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Online payments through PayPal. The browser only gets a payment page URL; a payment counts only after
 * the server asks PayPal whether that order is really completed, for the right amount and the right account.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PayPalService paypal;
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final JwtService jwt;
    private final UpiService upi;
    private final boolean demoMode;
    private final int registrationFee;

    public PaymentController(PayPalService paypal, UserRepository users, ProductRepository products,
                             OrderRepository orders, JwtService jwt, UpiService upi,
                             @Value("${app.demo-mode}") boolean demoMode,
                             @Value("${app.registration-fee}") int registrationFee) {
        this.paypal = paypal; this.users = users; this.products = products;
        this.orders = orders; this.jwt = jwt;
        this.upi = upi;
        this.demoMode = demoMode; this.registrationFee = registrationFee;
    }

    private void requirePayPal() {
        if (!paypal.enabled()) throw new ResponseStatusException(BAD_REQUEST, "Online payments are not set up");
    }

    private AuthRes paidAuth(User u) {
        return new AuthRes(jwt.generate(u, true), u.getName(), u.getRole().name(), true);
    }

    private static long cents(String value) {
        return new BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("online", paypal.enabled(), "demo", demoMode && !paypal.enabled() && !upi.enabled(), "provider", "PayPal", "fee", registrationFee);
    }

    @PostMapping("/fee-session")
    public Map<String, String> feeSession(Authentication auth) {
        requirePayPal();
        User u = users.findByEmail(auth.getName()).orElseThrow();
        if (!Boolean.FALSE.equals(u.getFeePaid()))
            throw new ResponseStatusException(CONFLICT, "Registration fee is already paid");
        String url = paypal.createOrder(List.of(new Item("AppBazaar registration fee", registrationFee * 100L)),
                "fee|" + u.getEmail());
        return Map.of("url", url);
    }

    @PostMapping("/order-session")
    public Map<String, String> orderSession(@Valid @RequestBody CheckoutReq r, Authentication auth) {
        requirePayPal();
        List<Long> ids = r.productIds().stream().distinct().toList();
        List<Product> items = products.findAllById(ids);
        if (items.size() != ids.size() || items.stream().anyMatch(p -> p.getStatus() != Status.LIVE))
            throw new ResponseStatusException(BAD_REQUEST, "Some products are no longer available");
        // with real money, never sell a listing that has no file to deliver (sample listings have none)
        if (items.stream().anyMatch(p -> p.getStoredFileName() == null))
            throw new ResponseStatusException(BAD_REQUEST, "Some products in your cart have no file yet and cannot be bought");
        List<Item> lines = items.stream().map(p -> new Item(p.getName(), p.getPrice() * 100L)).toList(); // price from the database
        String idList = String.join(",", ids.stream().map(String::valueOf).toList());
        String url = paypal.createOrder(lines, "order|" + auth.getName() + "|" + idList);
        return Map.of("url", url);
    }

    /** Called after PayPal sends the buyer back. Safe to call twice: it never fulfils the same payment twice. */
    @PostMapping("/confirm")
    @Transactional
    public ConfirmRes confirm(@Valid @RequestBody ConfirmReq r, Authentication auth) {
        requirePayPal();
        JsonNode o = paypal.getOrder(r.paymentId());
        if ("APPROVED".equals(o.path("status").asText())) {
            paypal.capture(r.paymentId());
            o = paypal.getOrder(r.paymentId());
        }
        if (!"COMPLETED".equals(o.path("status").asText()))
            throw new ResponseStatusException(PAYMENT_REQUIRED, "Payment was not completed");
        JsonNode unit = o.path("purchase_units").path(0);
        String[] parts = unit.path("custom_id").asText().split("\\|");
        if (parts.length < 2 || !auth.getName().equals(parts[1]))
            throw new ResponseStatusException(FORBIDDEN, "This payment belongs to another account");
        if (!"USD".equals(unit.path("amount").path("currency_code").asText()))
            throw new ResponseStatusException(CONFLICT, "Unexpected payment currency");
        long paid = cents(unit.path("amount").path("value").asText());
        String ref = o.path("id").asText();
        User u = users.findByEmail(auth.getName()).orElseThrow();

        if ("fee".equals(parts[0])) {
            if (paid != registrationFee * 100L)
                throw new ResponseStatusException(CONFLICT, "Payment amount does not match the registration fee");
            if (Boolean.FALSE.equals(u.getFeePaid())) {
                u.setFeePaid(true);
                u.setFeePaidAt(Instant.now());
                u.setFeePaymentRef(ref);
                users.save(u);
            }
            return new ConfirmRes("fee", paidAuth(u), null);
        }
        if ("order".equals(parts[0]) && parts.length > 2) {
            Orders existing = orders.findByPaymentRef(ref).orElse(null);
            if (existing == null) {
                List<Long> ids = Arrays.stream(parts[2].split(",")).map(Long::valueOf).toList();
                List<Product> items = products.findAllById(ids);
                int total = items.stream().mapToInt(Product::getPrice).sum();
                if (items.size() != ids.size() || paid != total * 100L)
                    throw new ResponseStatusException(CONFLICT, "Payment amount does not match the products");
                Orders o2 = new Orders();
                o2.setBuyer(u);
                o2.setItems(items);
                o2.setTotal(total);
                o2.setPaymentRef(ref);
                existing = orders.save(o2);
            }
            return new ConfirmRes("order", null, OrderRes.of(existing));
        }
        throw new ResponseStatusException(BAD_REQUEST, "Unknown payment type");
    }
}
