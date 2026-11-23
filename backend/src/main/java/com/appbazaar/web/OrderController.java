package com.appbazaar.web;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.web.Dto.*;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository orders;
    private final UserRepository users;

    public OrderController(OrderRepository orders, UserRepository users) {
        this.orders = orders;
        this.users = users;
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<OrderRes> mine(Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        return orders.findByBuyerOrderByIdDesc(u).stream().map(OrderRes::of).toList();
    }
}
