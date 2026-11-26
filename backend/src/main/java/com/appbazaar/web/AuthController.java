package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.UserRepository;
import com.appbazaar.security.JwtService;
import com.appbazaar.security.LoginLimiter;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginLimiter limiter;
    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, LoginLimiter limiter) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.limiter = limiter;
    }

    private AuthRes res(User u) {
        boolean paid = !Boolean.FALSE.equals(u.getFeePaid());
        String accountType = u.getAccountType();
        if (accountType == null || accountType.isBlank()) accountType = u.getRole() == Role.BUYER ? "BUYER" : "APP_SOFTWARE_SELLER";
        return new AuthRes(jwt.generate(u, paid), u.getName(), u.getRole().name(), paid, accountType);
    }

    @PostMapping("/login")
    public AuthRes login(@Valid @RequestBody LoginReq r) {
        String email = r.email().toLowerCase();
        if (limiter.blocked(email))
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many wrong tries. Try again in 15 minutes");
        User u = users.findByEmail(email)
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElse(null);
        if (u == null) {
            limiter.fail(email);
            throw new ResponseStatusException(UNAUTHORIZED, "Wrong email or password");
        }
        limiter.clear(email);
        return res(u);
    }

    /** Gives the logged-in user a fresh token that matches their current fee status after a payment is approved. */
    @PostMapping("/refresh")
    public AuthRes refresh(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(UNAUTHORIZED, "Please log in");
        return res(users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Please log in")));
    }

    @GetMapping("/fee-info")
    public Map<String, Integer> feeInfo() {
        return Map.of("buyer", 0, "appSoftwareSeller", 199, "gameDeveloper", 199, "saasDeveloper", 299);
    }
}
