package com.appbazaar.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** One pending email login code per address. Only a hash of the code is stored. */
@Entity
@Getter @Setter @NoArgsConstructor
public class LoginCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String email;
    private String codeHash;
    private Instant createdAt;
    private Instant expiresAt;
    private int attempts;
}
