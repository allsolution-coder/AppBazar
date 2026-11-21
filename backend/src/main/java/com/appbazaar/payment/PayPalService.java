package com.appbazaar.payment;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Talks to the PayPal Orders v2 API over HTTPS. Active only when the PayPal client id and secret are set. */
@Service
public class PayPalService {
    public record Item(String name, long cents) {}

    private final String clientId;
    private final String secret;
    private final String base;
    private final String frontendUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public PayPalService(@Value("${app.paypal-client-id}") String clientId,
                         @Value("${app.paypal-secret}") String secret,
                         @Value("${app.paypal-base}") String base,
                         @Value("${app.frontend-url}") String frontendUrl) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.base = base;
        this.frontendUrl = frontendUrl;
    }

    public boolean enabled() { return !clientId.isBlank() && !secret.isBlank(); }

    /** Creates a PayPal order and returns the page where the buyer approves the payment. */
    public String createOrder(List<Item> items, String customId) {
        long total = items.stream().mapToLong(Item::cents).sum();
        ObjectNode root = mapper.createObjectNode();
        root.put("intent", "CAPTURE");
        ObjectNode unit = root.putArray("purchase_units").addObject();
        unit.put("custom_id", customId);
        ObjectNode amount = unit.putObject("amount");
        amount.put("currency_code", "USD");
        amount.put("value", money(total));
        amount.putObject("breakdown").putObject("item_total").put("currency_code", "USD").put("value", money(total));
        ArrayNode arr = unit.putArray("items");
        for (Item i : items) {
            ObjectNode it = arr.addObject();
            it.put("name", i.name().length() > 120 ? i.name().substring(0, 120) : i.name());
            it.put("quantity", "1");
            it.putObject("unit_amount").put("currency_code", "USD").put("value", money(i.cents()));
        }
        ObjectNode ctx = root.putObject("application_context");
        ctx.put("brand_name", "AppBazaar");
        ctx.put("user_action", "PAY_NOW");
        ctx.put("return_url", frontendUrl + "/payment/success");
        ctx.put("cancel_url", frontendUrl + "/payment/cancel");
        JsonNode res = send(request("/v2/checkout/orders").POST(HttpRequest.BodyPublishers.ofString(root.toString())).build());
        for (JsonNode l : res.path("links")) {
            String rel = l.path("rel").asText();
            if (rel.equals("approve") || rel.equals("payer-action")) return l.path("href").asText();
        }
        throw new ResponseStatusException(BAD_GATEWAY, "PayPal did not return a payment page");
    }

    public JsonNode getOrder(String id) {
        return send(request("/v2/checkout/orders/" + enc(id)).GET().build());
    }

    public void capture(String id) {
        send(request("/v2/checkout/orders/" + enc(id) + "/capture")
                .header("PayPal-Request-Id", "capture-" + id)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json");
    }

    private String accessToken() {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/oauth2/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        return send(req).path("access_token").asText();
    }

    private JsonNode send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = mapper.readTree(res.body());
            if (res.statusCode() / 100 != 2) {
                String why = node.path("message").asText(node.path("error_description").asText("unknown"));
                throw new ResponseStatusException(BAD_GATEWAY, "Payment provider error: " + why);
            }
            return node;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Payment provider did not respond");
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Could not reach the payment provider");
        }
    }

    private static String money(long cents) { return String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
