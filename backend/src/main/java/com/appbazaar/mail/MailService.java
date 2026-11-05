package com.appbazaar.mail;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

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
 * Sends login codes by email. When no mail server is configured (local development), the code is
 * printed in the backend window instead so you can still log in.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender; // null when SPRING_MAIL_HOST is not set
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, @Value("${app.mail-from}") String from) {
        this.sender = sender.getIfAvailable();
        this.from = from;
    }

    public boolean configured() { return sender != null; }

    public void sendLoginCode(String to, String code) {
        if (sender == null) {
            log.warn("MAIL NOT CONFIGURED (development mode). Login code for {} is {}", to, code);
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject("Your AppBazaar login code");
        m.setText("Your AppBazaar login code is " + code + ".\n\nIt expires in 10 minutes. "
                + "If you did not ask for it, you can ignore this email.");
        try {
            sender.send(m);
        } catch (MailException e) {
            log.error("Could not send login email: {}", e.getMessage());
            throw new ResponseStatusException(BAD_GATEWAY, "Could not send the email. Please try again later.");
        }
    }
}
