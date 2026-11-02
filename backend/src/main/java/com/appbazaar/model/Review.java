package com.appbazaar.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Product product;
    @ManyToOne(optional = false)
    private User user;
    private int rating;
    @Column(length = 1000)
    private String comment;
    private Instant createdAt = Instant.now();
}
