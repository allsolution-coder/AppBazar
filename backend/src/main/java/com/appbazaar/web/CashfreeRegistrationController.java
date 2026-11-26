package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.Role;
import com.appbazaar.model.User;
import com.appbazaar.repo.UserRepository;
import com.appbazaar.security.JwtService;
import com.appbazaar.web.Dto.AuthRes;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** INR one-time developer registration fee. Account access changes only after Cashfree confirms PAID. */
@RestController
@RequestMapping("/api/payments/cashfree-registration")
public class CashfreeRegistrationController {
    private static final String CURRENCY = "INR";
    private final UserRepository users;
    private final JwtService jwt;
    private final String clientId;
    private final String secret;
    private final String frontendUrl;
    private final String sdkMode;
    private final boolean enabled;
    private final RestClient cashfree;

    public CashfreeRegistrationController(UserRepository users, JwtService jwt,
            @Value("${app.cashfree-client-id:}") String clientId,
            @Value("${app.cashfree-secret:}") String secret,
            @Value("${app.cashfree-mode:sandbox}") String mode,
            @Value("${app.cashfree-api-version:2025-01-01}") String apiVersion,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.jwt = jwt;
        this.clientId = clientId.trim();
        this.secret = secret.trim();
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        boolean live = "production".equalsIgnoreCase(mode.trim());
        this.sdkMode = live ? "production" : "sandbox";
        boolean localSandbox = !live && (this.frontendUrl.startsWith("http://localhost:")
                || this.frontendUrl.startsWith("http://127.0.0.1:"));
        // Never let a sandbox transaction unlock a production account.
        this.enabled = !this.clientId.isBlank() && !this.secret.isBlank() && (live || localSandbox);
        String baseUrl = live ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.cashfree = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("x-api-version", apiVersion.trim())
                .defaultHeader("x-client-id", this.clientId)
                .defaultHeader("x-client-secret", this.secret).build();
    }

    public record CheckoutRequest(@NotBlank @Pattern(regexp = "[0-9]{10,15}", message = "Enter a valid phone number") String phone) {}

    @GetMapping("/config")
    public Map<String, Object> config(Authentication auth) {
        User user = current(auth);
        String type = accountType(user);
        return Map.of("enabled", enabled, "mode", sdkMode,
                "amount", fee(type), "currency", CURRENCY, "accountType", type);
    }

    @PostMapping("/orders")
    public Map<String, String> create(@Valid @RequestBody CheckoutRequest input, Authentication auth) {
        requireEnabled();
        User user = current(auth);
        if (!Boolean.FALSE.equals(user.getFeePaid()))
            throw new ResponseStatusException(CONFLICT, "Registration is already complete");
        int amount = fee(accountType(user));
        if (amount <= 0) throw new ResponseStatusException(BAD_REQUEST, "Buyer accounts do not have a registration fee");

        String orderId = "ab" + user.getId() + "_" + UUID.randomUUID().toString().replace("-", "");
        String returnUrl = frontendUrl + "/fee?cashfree_order_id={order_id}";
        JsonNode order = request(cashfree.post().uri("/orders").body(Map.of(
                "order_id", orderId,
                "order_amount", amount,
                "order_currency", CURRENCY,
                "customer_details", Map.of(
                        "customer_id", "ab_" + user.getId(),
                        "customer_name", user.getName(),
                        "customer_email", user.getEmail(),
                        "customer_phone", input.phone()),
                "order_meta", Map.of("return_url", returnUrl),
                "order_note", "AppBazaar " + accountType(user) + " one-time registration fee")));
        String session = order.path("payment_session_id").asText();
        if (!orderId.equals(order.path("order_id").asText()) || session.isBlank())
            throw new ResponseStatusException(BAD_GATEWAY, "Cashfree returned an invalid checkout session");
        return Map.of("orderId", orderId, "paymentSessionId", session);
    }

    @PostMapping("/orders/{orderId}/verify")
    @Transactional
    public AuthRes verify(@PathVariable String orderId, Authentication auth) {
        requireEnabled();
        User user = users.findByEmailForUpdate(auth.getName()).orElseThrow();
        String expectedPrefix = "ab" + user.getId() + "_";
        if (!orderId.startsWith(expectedPrefix) || !orderId.matches("ab[0-9]+_[a-f0-9]{32}"))
            throw new ResponseStatusException(NOT_FOUND, "Registration order not found");

        JsonNode order = request(cashfree.get().uri("/orders/{id}", orderId));
        int amount = fee(accountType(user));
        BigDecimal paid = order.path("order_amount").isNumber()
                ? order.path("order_amount").decimalValue() : BigDecimal.valueOf(-1);
        if (!orderId.equals(order.path("order_id").asText())
                || !"PAID".equals(order.path("order_status").asText())
                || !CURRENCY.equals(order.path("order_currency").asText())
                || paid.compareTo(BigDecimal.valueOf(amount)) != 0)
            throw new ResponseStatusException(PAYMENT_REQUIRED, "Cashfree has not confirmed the correct registration payment yet");

        if (Boolean.FALSE.equals(user.getFeePaid())) {
            user.setFeePaid(true);
            user.setFeePaidAt(Instant.now());
            user.setFeePaymentRef(orderId);
            user.setFeeAmountInr(amount);
        }
        return new AuthRes(jwt.generate(user, true), user.getName(), user.getRole().name(), true, accountType(user));
    }

    private User current(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(UNAUTHORIZED, "Please log in");
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Please log in"));
    }

    private static String accountType(User user) {
        if (user.getAccountType() != null && !user.getAccountType().isBlank()) return user.getAccountType();
        return user.getRole() == Role.BUYER ? "BUYER" : "APP_SOFTWARE_SELLER";
    }

    private static int fee(String type) {
        return switch (type) {
            case "BUYER" -> 0;
            case "SAAS_DEVELOPER" -> 299;
            case "GAME_DEVELOPER", "APP_SOFTWARE_SELLER" -> 199;
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unknown account type");
        };
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Cashfree registration payments are not configured");
    }

    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode result = request.retrieve().body(JsonNode.class);
            if (result != null) return result;
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Cashfree request failed. Check the configured environment and credentials.");
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Cashfree returned an empty response");
    }
}
