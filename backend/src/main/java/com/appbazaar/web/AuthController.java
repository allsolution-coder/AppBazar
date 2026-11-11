package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.payment.PayPalService;
import com.appbazaar.payment.UpiService;
import com.appbazaar.repo.UserRepository;
import com.appbazaar.security.JwtService;
import com.appbazaar.security.LoginLimiter;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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
    private final PayPalService paypal;
    private final LoginLimiter limiter;
    private final UpiService upi;
    private final int registrationFee;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, PayPalService paypal, LoginLimiter limiter, UpiService upi,
                          @Value("${app.registration-fee}") int registrationFee) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.paypal = paypal; this.limiter = limiter; this.upi = upi; this.registrationFee = registrationFee;
    }

    private AuthRes res(User u) {
        boolean paid = !Boolean.FALSE.equals(u.getFeePaid());
        return new AuthRes(jwt.generate(u, paid), u.getName(), u.getRole().name(), paid);
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

    /** Gives the logged-in user a fresh token that matches their current fee status (used after a UPI payment is approved). */
    @PostMapping("/refresh")
    public AuthRes refresh(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(UNAUTHORIZED, "Please log in");
        return res(users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Please log in")));
    }

    @GetMapping("/fee-info")
    public Map<String, Integer> feeInfo() {
        return Map.of("amount", registrationFee);
    }

    /** Demo payment, used only while PayPal is not configured. With PayPal on, the fee is confirmed by PaymentController. */
    @PostMapping("/pay-fee")
    public AuthRes payFee(Authentication auth) {
        if (paypal.enabled() || upi.enabled())
            throw new ResponseStatusException(BAD_REQUEST, "Online payments are on. Use the Pay button on the payment page.");
        User u = users.findByEmail(auth.getName()).orElseThrow();
        if (!Boolean.FALSE.equals(u.getFeePaid()))
            throw new ResponseStatusException(CONFLICT, "Registration fee is already paid");
        u.setFeePaid(true);
        u.setFeePaidAt(Instant.now());
        users.save(u);
        return res(u);
    }
}
