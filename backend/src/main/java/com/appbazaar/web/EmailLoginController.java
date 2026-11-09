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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sign-up and password reset. The email is checked once with a 6-digit code (sign-up, forgot password),
 * so nobody can create or take over an account for an address they do not own. Normal logins use the password.
 */
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

    @PostMapping("/signup")
    @Transactional
    public Map<String, Object> signup(@Valid @RequestBody SignupReq r) {
        String email = norm(r.email());
        if (users.findByEmail(email).isPresent())
            throw new ResponseStatusException(CONFLICT, "This email is already registered. Log in, or use Forgot password");
        String role = "SELLER".equalsIgnoreCase(r.role()) ? "SELLER" : "BUYER"; // admins are never created here
        issue(email, "signup", encoder.encode(r.password()), role);
        return Map.of("sent", true, "devMode", !mail.configured());
    }

    // wrong tries must be saved even though the request fails
    @PostMapping("/signup/verify")
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthRes signupVerify(@Valid @RequestBody VerifyReq r) {
        String email = norm(r.email());
        LoginCode lc = check(email, r.code(), "signup");
        if (users.findByEmail(email).isPresent())
            throw new ResponseStatusException(CONFLICT, "This email is already registered. Please log in");
        String name = email.substring(0, email.indexOf('@'));
        User u = new User();
        u.setName(name.length() > 40 ? name.substring(0, 40) : name);
        u.setEmail(email);
        u.setPasswordHash(lc.getPendingPasswordHash());
        u.setRole("SELLER".equals(lc.getPendingRole()) ? Role.SELLER : Role.BUYER);
        u.setFeePaid(false); // new accounts pay the registration fee
        users.save(u);
        codes.delete(lc);
        return auth(u);
    }

    @PostMapping("/forgot")
    @Transactional
    public Map<String, Object> forgot(@Valid @RequestBody CodeReq r) {
        String email = norm(r.email());
        // the answer is the same whether or not the email exists, so it cannot be used to look people up
        users.findByEmail(email).filter(u -> u.getRole() != Role.ADMIN).ifPresent(u -> issue(email, "reset", null, null));
        return Map.of("sent", true, "devMode", !mail.configured());
    }

    @PostMapping("/reset")
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthRes reset(@Valid @RequestBody ResetReq r) {
        String email = norm(r.email());
        LoginCode lc = check(email, r.code(), "reset");
        User u = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Ask for a new code first"));
        if (u.getRole() == Role.ADMIN) throw new ResponseStatusException(FORBIDDEN, "The admin password cannot be reset here");
        u.setPasswordHash(encoder.encode(r.password()));
        users.save(u);
        codes.delete(lc);
        return auth(u);
    }

    private AuthRes auth(User u) {
        boolean paid = !Boolean.FALSE.equals(u.getFeePaid());
        return new AuthRes(jwt.generate(u, paid), u.getName(), u.getRole().name(), paid);
    }

    private void issue(String email, String purpose, String pendingHash, String pendingRole) {
        LoginCode lc = codes.findByEmail(email).orElseGet(LoginCode::new);
        if (lc.getCreatedAt() != null && lc.getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Please wait a minute before asking for another code");
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        lc.setEmail(email);
        lc.setCodeHash(hash(email, code));
        lc.setCreatedAt(Instant.now());
        lc.setExpiresAt(Instant.now().plusSeconds(600));
        lc.setAttempts(0);
        lc.setPurpose(purpose);
        lc.setPendingPasswordHash(pendingHash);
        lc.setPendingRole(pendingRole);
        codes.save(lc);
        mail.sendLoginCode(email, code);
    }

    /** Checks the code (right purpose, not expired, not too many tries). Returns the row; the caller deletes it. */
    private LoginCode check(String email, String code, String purpose) {
        LoginCode lc = codes.findByEmail(email)
                .filter(x -> purpose.equals(x.getPurpose()))
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
                hash(email, code.trim()).getBytes(StandardCharsets.UTF_8),
                lc.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            lc.setAttempts(lc.getAttempts() + 1);
            codes.save(lc);
            throw new ResponseStatusException(BAD_REQUEST, "Wrong code");
        }
        return lc;
    }

    private static String norm(String email) { return email.trim().toLowerCase(); }

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
