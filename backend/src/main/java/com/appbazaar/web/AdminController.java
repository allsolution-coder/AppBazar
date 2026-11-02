package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.storage.FileStorage;
import com.appbazaar.web.Dto.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ProductRepository products;
    private final OrderRepository orders;
    private final FileStorage storage;
    private final UserRepository users;
    private final int registrationFee;
    private final double commission;

    public AdminController(ProductRepository products, OrderRepository orders, FileStorage storage, UserRepository users,
                           @Value("${app.registration-fee}") int registrationFee,
                           @Value("${app.commission}") double commission) {
        this.products = products; this.orders = orders; this.storage = storage; this.users = users;
        this.registrationFee = registrationFee; this.commission = commission;
    }

    @GetMapping("/stats")
    public Stats stats() {
        long revenue = orders.totalRevenue();
        return new Stats(products.countByStatus(Status.LIVE), products.countByStatus(Status.PENDING),
                orders.count(), revenue, Math.round(revenue * commission),
                users.countByFeePaidAtNotNull() * (long) registrationFee);
    }

    @GetMapping("/pending")
    public List<ProductRes> pending() {
        return products.findByStatusOrderByIdDesc(Status.PENDING).stream().map(ProductRes::of).toList();
    }

    @PostMapping("/products/{id}/approve")
    public ProductRes approve(@PathVariable Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Not found"));
        p.setStatus(Status.LIVE);
        return ProductRes.of(products.save(p));
    }

    @DeleteMapping("/products/{id}")
    public void reject(@PathVariable Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Not found"));
        if (p.getStatus() != Status.PENDING)
            throw new ResponseStatusException(CONFLICT, "Only pending products can be rejected");
        storage.delete(p.getStoredFileName());
        products.delete(p);
    }
}
