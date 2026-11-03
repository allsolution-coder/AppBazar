package com.appbazaar.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
public class Orders {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User buyer;
    private int total;
    private Instant createdAt = Instant.now();
    @Column(unique = true)
    private String paymentRef; // PayPal order id, null for demo orders
    @ManyToMany
    private List<Product> items;
}
