package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReviewController {
    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;

    public ReviewController(ReviewRepository reviews, ProductRepository products,
                            OrderRepository orders, UserRepository users) {
        this.reviews = reviews; this.products = products; this.orders = orders; this.users = users;
    }

    @GetMapping("/api/products/{id:\\d+}/reviews")
    public List<ReviewRes> list(@PathVariable Long id) {
        return reviews.findByProductIdOrderByIdDesc(id).stream().map(ReviewRes::of).toList();
    }

    /** One review per buyer per product. Posting again edits the earlier review. */
    @PostMapping("/api/products/{id:\\d+}/reviews")
    @Transactional
    public ReviewRes post(@PathVariable Long id, @Valid @RequestBody ReviewReq req, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        Product p = products.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        if (p.getSeller().getId().equals(u.getId()))
            throw new ResponseStatusException(FORBIDDEN, "You can't review your own product");
        if (!orders.hasPurchased(u, id))
            throw new ResponseStatusException(FORBIDDEN, "Only buyers of this product can review it");
        Review r = reviews.findByProductIdAndUserId(id, u.getId()).orElseGet(Review::new);
        r.setProduct(p);
        r.setUser(u);
        r.setRating(req.rating());
        r.setComment(req.comment().trim());
        r.setCreatedAt(Instant.now());
        reviews.saveAndFlush(r);
        Double avg = reviews.avgRating(id);
        p.setRating(Math.round(avg * 10) / 10.0);
        p.setReviewCount((int) reviews.countByProductId(id));
        products.save(p);
        return ReviewRes.of(r);
    }
}
