package com.appbazaar.mail;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends login codes by email, using the first option that is configured:
 * 1. Brevo HTTPS API (BREVO_API_KEY). Use this on free hosts that block SMTP, such as Render.
 * 2. SMTP (SPRING_MAIL_HOST), for example Gmail while developing on your own computer.
 * 3. Neither: the code is printed in the backend console (local development only).
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender smtp; // null when SPRING_MAIL_HOST is not set
    private final String from;
    private final String brevoKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public MailService(ObjectProvider<JavaMailSender> smtp, @Value("${app.mail-from}") String from,
                       @Value("${app.brevo-api-key}") String brevoKey) {
        this.smtp = smtp.getIfAvailable();
        this.from = from;
        this.brevoKey = brevoKey == null ? "" : brevoKey.trim();
    }

    public boolean configured() { return !brevoKey.isBlank() || smtp != null; }

    public void sendLoginCode(String to, String code) {
        String subject = "Your AppBazaar login code";
        String text = "Your AppBazaar login code is " + code + ".\n\nIt expires in 10 minutes. "
                + "If you did not ask for it, you can ignore this email.";
        if (!brevoKey.isBlank()) {
            sendWithBrevo(to, subject, text);
        } else if (smtp != null) {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(text);
            try {
                smtp.send(m);
            } catch (MailException e) {
                log.error("Could not send login email: {}", e.getMessage());
                throw new ResponseStatusException(BAD_GATEWAY, "Could not send the email. Please try again later.");
            }
        } else {
            log.warn("MAIL NOT CONFIGURED (development mode). Login code for {} is {}", to, code);
        }
    }

    private void sendWithBrevo(String to, String subject, String text) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("sender").put("name", "AppBazaar").put("email", from);
        body.putArray("to").addObject().put("email", to);
        body.put("subject", subject);
        body.put("textContent", text);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", brevoKey)
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.error("Brevo rejected the email ({}): {}", res.statusCode(), res.body());
                throw new ResponseStatusException(BAD_GATEWAY, "Could not send the email. Please try again later.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Could not send the email. Please try again later.");
        } catch (IOException e) {
            log.error("Could not reach Brevo: {}", e.getMessage());
            throw new ResponseStatusException(BAD_GATEWAY, "Could not send the email. Please try again later.");
        }
    }
}
