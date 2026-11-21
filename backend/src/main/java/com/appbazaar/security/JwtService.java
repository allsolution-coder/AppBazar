package com.appbazaar.security;

import com.appbazaar.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${app.jwt-secret}") String secret) {
        if (secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.equals("change-this-secret-to-something-long-and-random-32+chars")) {
            throw new IllegalStateException("Configure JWT_SECRET with at least 32 bytes of random data");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(User u, boolean paid) {
        return Jwts.builder()
                .subject(u.getEmail())
                .claim("role", u.getRole().name())
                .claim("paid", paid)
                .claim("credentialVersion", credentialVersion(u))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 12L * 3600 * 1000))
                .signWith(key)
                .compact();
    }

    static String credentialVersion(User user) {
        try {
            String value = user.getId() + ":" + user.getPasswordHash();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
