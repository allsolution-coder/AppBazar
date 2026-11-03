package com.appbazaar.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(unique = true, nullable = false)
    private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    private Boolean feePaid;      // null means an account created before the fee existed (treated as paid)
    private Instant feePaidAt;
    private String feePaymentRef;
}
