package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.security.LoginLimiter;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The logged-in user's own account: details, display name and password. */
@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PasswordEncoder encoder;
    private final LoginLimiter limiter;

    public AccountController(UserRepository users, ProductRepository products, OrderRepository orders,
                             PasswordEncoder encoder, LoginLimiter limiter) {
        this.users = users; this.products = products; this.orders = orders;
        this.encoder = encoder; this.limiter = limiter;
    }

    private User current(Authentication auth) {
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Please log in"));
    }

    private AccountRes view(User u) {
        String type = u.getAccountType();
        if (type == null || type.isBlank()) type = u.getRole() == Role.BUYER ? "BUYER" : "APP_SOFTWARE_SELLER";
        return new AccountRes(u.getName(), u.getEmail(), u.getRole().name(), type,
                !Boolean.FALSE.equals(u.getFeePaid()), u.getFeePaidAt(), u.getFeeAmountInr(),
                orders.countByBuyer(u), products.countBySeller(u));
    }

    @GetMapping("/me")
    public AccountRes me(Authentication auth) {
        return view(current(auth));
    }

    @PutMapping("/me")
    public AccountRes rename(@Valid @RequestBody NameReq r, Authentication auth) {
        User u = current(auth);
        u.setName(r.name().trim());
        return view(users.save(u));
    }

    @PostMapping("/password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody PasswordReq r, Authentication auth) {
        User u = current(auth);
        String key = "pw:" + u.getEmail();
        if (limiter.blocked(key))
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many wrong tries. Try again in 15 minutes");
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
            limiter.fail(key);
            throw new ResponseStatusException(BAD_REQUEST, "Your current password is wrong. If you forgot it, log out and use Forgot password");
        }
        limiter.clear(key);
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
        return Map.of("ok", true);
    }
}
