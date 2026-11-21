package com.appbazaar.web;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.appbazaar.model.*;
import com.appbazaar.payment.PayPalService;
import com.appbazaar.payment.UpiService;
import com.appbazaar.repo.*;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;
    private final PayPalService paypal;
    private final boolean demoMode;
    private final UpiService upi;

    public OrderController(OrderRepository orders, ProductRepository products, UserRepository users, PayPalService paypal, UpiService upi, @Value("${app.demo-mode}") boolean demoMode) {
        this.orders = orders; this.products = products; this.users = users; this.paypal = paypal; this.upi = upi;
        this.demoMode = demoMode;
    }

    /** Demo checkout, used only while PayPal is not configured. With PayPal on, orders are created by PaymentController. */
    @PostMapping
    @Transactional
    public OrderRes checkout(@Valid @RequestBody CheckoutReq r, Authentication auth) {
        if (paypal.enabled() || upi.enabled())
            throw new ResponseStatusException(BAD_REQUEST, "Online payments are on. Use the Pay with PayPal button.");
        if (!demoMode) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Payments are currently unavailable");
        List<Product> items = products.findAllById(r.productIds());
        if (items.size() != r.productIds().stream().distinct().count()
                || items.stream().anyMatch(p -> p.getStatus() != Status.LIVE))
            throw new ResponseStatusException(BAD_REQUEST, "Some products are no longer available");
        Orders o = new Orders();
        o.setBuyer(users.findByEmail(auth.getName()).orElseThrow());
        o.setItems(items);
        o.setTotal(items.stream().mapToInt(Product::getPrice).sum()); // price always comes from the database
        return OrderRes.of(orders.save(o));
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<OrderRes> mine(Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        return orders.findByBuyerOrderByIdDesc(u).stream().map(OrderRes::of).toList();
    }
}
