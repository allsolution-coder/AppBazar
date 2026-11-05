package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.mail.MailService;
import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.security.JwtService;
import com.appbazaar.web.Dto.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Passwordless login and sign-up: the user only enters an email, then the 6-digit code sent to it. */
@RestController
@RequestMapping("/api/auth")
public class EmailLoginController {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    private final LoginCodeRepository codes;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MailService mail;
    private final String pepper;

    public EmailLoginController(LoginCodeRepository codes, UserRepository users, PasswordEncoder encoder,
                                JwtService jwt, MailService mail, @Value("${app.jwt-secret}") String pepper) {
        this.codes = codes; this.users = users; this.encoder = encoder;
        this.jwt = jwt; this.mail = mail; this.pepper = pepper;
    }

    @PostMapping("/request-code")
    @Transactional
    public Map<String, Object> requestCode(@Valid @RequestBody CodeReq r) {
        String email = r.email().trim().toLowerCase();
        users.findByEmail(email).ifPresent(u -> {
            if (u.getRole() == Role.ADMIN)
                throw new ResponseStatusException(FORBIDDEN, "Admin accounts sign in with a password");
        });
        LoginCode lc = codes.findByEmail(email).orElseGet(LoginCode::new);
        if (lc.getCreatedAt() != null && lc.getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Please wait a minute before asking for another code");
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        lc.setEmail(email);
        lc.setCodeHash(hash(email, code));
        lc.setCreatedAt(Instant.now());
        lc.setExpiresAt(Instant.now().plusSeconds(600));
        lc.setAttempts(0);
        codes.save(lc);
        mail.sendLoginCode(email, code);
        return Map.of("sent", true, "devMode", !mail.configured());
    }

    // wrong tries must be saved even though the request fails
    @PostMapping("/verify-code")
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthRes verifyCode(@Valid @RequestBody VerifyReq r) {
        String email = r.email().trim().toLowerCase();
        LoginCode lc = codes.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Ask for a new code first"));
        if (lc.getExpiresAt().isBefore(Instant.now())) {
            codes.delete(lc);
            throw new ResponseStatusException(BAD_REQUEST, "The code expired. Ask for a new one");
        }
        if (lc.getAttempts() >= MAX_ATTEMPTS) {
            codes.delete(lc);
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many wrong tries. Ask for a new code");
        }
        boolean ok = MessageDigest.isEqual(
                hash(email, r.code().trim()).getBytes(StandardCharsets.UTF_8),
                lc.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            lc.setAttempts(lc.getAttempts() + 1);
            codes.save(lc);
            throw new ResponseStatusException(BAD_REQUEST, "Wrong code");
        }
        codes.delete(lc);
        User u = users.findByEmail(email).orElseGet(() -> newUser(email, r.role()));
        if (u.getRole() == Role.ADMIN)
            throw new ResponseStatusException(FORBIDDEN, "Admin accounts sign in with a password");
        boolean paid = !Boolean.FALSE.equals(u.getFeePaid());
        return new AuthRes(jwt.generate(u, paid), u.getName(), u.getRole().name(), paid);
    }

    private User newUser(String email, String role) {
        String name = email.substring(0, email.indexOf('@'));
        User u = new User();
        u.setName(name.length() > 40 ? name.substring(0, 40) : name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(UUID.randomUUID().toString())); // nobody knows it: this account uses email codes
        u.setRole("SELLER".equalsIgnoreCase(role) ? Role.SELLER : Role.BUYER); // admins are never created here
        u.setFeePaid(false); // new accounts pay the registration fee
        return users.save(u);
    }

    private String hash(String email, String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest((code + ":" + email + ":" + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
