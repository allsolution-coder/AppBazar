package com.appbazaar.web;

import com.appbazaar.model.User;
import com.appbazaar.repo.UserRepository;
import com.appbazaar.security.JwtService;
import com.appbazaar.web.Dto.AuthRes;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/payments/razorpay-registration")
public class RazorpayRegistrationController {
    private static final int AMOUNT = 10000;
    private static final String CURRENCY = "INR";
    private final UserRepository users;
    private final JwtService jwt;
    private final String keyId;
    private final String secret;
    private final boolean enabled;
    private final RestClient razorpay;

    public RazorpayRegistrationController(UserRepository users, JwtService jwt,
            @Value("${RAZORPAY_TEST_KEY_ID:}") String keyId,
            @Value("${RAZORPAY_TEST_KEY_SECRET:}") String secret,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.jwt = jwt;
        this.keyId = keyId.trim();
        this.secret = secret.trim();
        this.enabled = "http://localhost:5173".equals(frontendUrl)
                && this.keyId.startsWith("rzp_test_") && !this.secret.isBlank();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.razorpay = RestClient.builder().baseUrl("https://api.razorpay.com/v1")
                .requestFactory(factory).build();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("enabled", enabled, "amount", AMOUNT, "currency", CURRENCY);
    }

    @PostMapping("/order")
    public Map<String, Object> create(Authentication auth) {
        requireEnabled();
        User user = users.findByEmail(auth.getName()).orElseThrow();
        if (!Boolean.FALSE.equals(user.getFeePaid()))
            throw new ResponseStatusException(CONFLICT, "Registration fee is already paid");

        JsonNode order = request(razorpay.post().uri("/orders").body(Map.of(
                "amount", AMOUNT, "currency", CURRENCY,
                "notes", Map.of("appbazaar_registration_user", user.getId().toString()))));
        return Map.of("keyId", keyId, "orderId", order.path("id").asText(),
                "amount", AMOUNT, "currency", CURRENCY);
    }

    public record Verification(
            @Pattern(regexp = "order_[A-Za-z0-9]{1,60}") @jakarta.validation.constraints.NotBlank String orderId,
            @Pattern(regexp = "pay_[A-Za-z0-9]{1,60}") @jakarta.validation.constraints.NotBlank String paymentId,
            @Pattern(regexp = "[a-fA-F0-9]{64}") @jakarta.validation.constraints.NotBlank String signature) {}

    @PostMapping("/verify")
    @Transactional
    public AuthRes verify(@Valid @RequestBody Verification input, Authentication auth) {
        requireEnabled();
        User user = users.findByEmailForUpdate(auth.getName()).orElseThrow();
        JsonNode order = request(razorpay.get().uri("/orders/{id}", input.orderId()));
        if (!input.orderId().equals(order.path("id").asText())
                || !user.getId().toString().equals(order.path("notes").path("appbazaar_registration_user").asText())
                || !matchesSignature(order.path("id").asText(), input))
            throw new ResponseStatusException(FORBIDDEN, "Payment verification failed");

        JsonNode payment = request(razorpay.get().uri("/payments/{id}", input.paymentId()));
        if (!input.paymentId().equals(payment.path("id").asText())
                || !input.orderId().equals(payment.path("order_id").asText())
                || !"paid".equals(order.path("status").asText())
                || order.path("amount").asInt() != AMOUNT || !CURRENCY.equals(order.path("currency").asText())
                || payment.path("amount").asInt() != AMOUNT || !CURRENCY.equals(payment.path("currency").asText())
                || !"captured".equals(payment.path("status").asText()))
            throw new ResponseStatusException(PAYMENT_REQUIRED, "Test payment is not captured for INR 100");

        if (Boolean.FALSE.equals(user.getFeePaid())) {
            user.setFeePaid(true);
            user.setFeePaidAt(Instant.now());
            user.setFeePaymentRef(input.paymentId());
        }
        return new AuthRes(jwt.generate(user, true), user.getName(), user.getRole().name(), true);
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Razorpay Test Mode is unavailable");
    }

    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode result = request.headers(headers -> headers.setBasicAuth(keyId, secret))
                    .retrieve().body(JsonNode.class);
            if (result != null) return result;
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Razorpay request failed. Check the Test Mode keys.");
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Razorpay returned an empty response");
    }

    private boolean matchesSignature(String orderId, Verification input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((orderId + "|" + input.paymentId()).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(input.signature()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
