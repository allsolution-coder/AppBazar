package com.appbazaar.web;

import com.appbazaar.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/payments/cashfree-registration-test")
public class CashfreeRegistrationController {
    private static final BigDecimal FEE = new BigDecimal("100");
    private final UserRepository users;
    private final RestClient cashfree;
    private final String tester;
    private final boolean enabled;

    public CashfreeRegistrationController(UserRepository users,
            @Value("${CASHFREE_TEST_CLIENT_ID:}") String clientId,
            @Value("${CASHFREE_TEST_CLIENT_SECRET:}") String secret,
            @Value("${CASHFREE_TEST_ACCOUNT_EMAIL:}") String tester) {
        this.users = users;
        this.tester = tester.trim();
        this.enabled = !clientId.isBlank() && !secret.isBlank() && !this.tester.isBlank();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.cashfree = RestClient.builder().baseUrl("https://sandbox.cashfree.com/pg")
                .requestFactory(factory)
                .defaultHeader("x-api-version", "2025-01-01")
                .defaultHeader("x-client-id", clientId.trim())
                .defaultHeader("x-client-secret", secret.trim()).build();
    }

    @GetMapping("/config")
    public Map<String, Object> config(Authentication auth) {
        return Map.of("enabled", allowed(auth), "amount", FEE, "currency", "INR");
    }

    @PostMapping("/orders")
    public Map<String, String> create(Authentication auth) {
        String customer = customerId(auth);
        String orderId = customer + "_" + UUID.randomUUID().toString().replace("-", "");
        JsonNode order = request(cashfree.post().uri("/orders").body(Map.of(
                "order_id", orderId, "order_amount", FEE, "order_currency", "INR",
                "customer_details", Map.of("customer_id", customer, "customer_phone", "9999999999"),
                "order_note", "AppBazaar sandbox registration test")));
        String session = order.path("payment_session_id").asText();
        if (!orderId.equals(order.path("order_id").asText()) || session.isBlank())
            throw new ResponseStatusException(BAD_GATEWAY, "Cashfree returned an invalid checkout session");
        return Map.of("orderId", orderId, "paymentSessionId", session);
    }

    @GetMapping("/orders/{orderId}")
    public Map<String, Boolean> verify(@PathVariable String orderId, Authentication auth) {
        String customer = customerId(auth);
        if (!orderId.matches(customer + "_[a-f0-9]{32}"))
            throw new ResponseStatusException(NOT_FOUND, "Test order not found");
        JsonNode order = request(cashfree.get().uri("/orders/{id}", orderId));
        JsonNode amount = order.path("order_amount");
        boolean paid = orderId.equals(order.path("order_id").asText())
                && customer.equals(order.path("customer_details").path("customer_id").asText())
                && "PAID".equals(order.path("order_status").asText())
                && "INR".equals(order.path("order_currency").asText())
                && amount.isNumber() && FEE.compareTo(amount.decimalValue()) == 0;
        return Map.of("paid", paid);
    }

    private boolean allowed(Authentication auth) {
        return enabled && auth != null && tester.equalsIgnoreCase(auth.getName());
    }

    private String customerId(Authentication auth) {
        if (!allowed(auth)) throw new ResponseStatusException(NOT_FOUND, "Cashfree test checkout unavailable");
        return users.findByEmail(auth.getName()).map(user -> "ab_" + user.getId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Please log in"));
    }

    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode result = request.retrieve().body(JsonNode.class);
            if (result != null) return result;
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Cashfree sandbox request failed");
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Cashfree returned an empty response");
    }
}
