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
    @ManyToMany
    private List<Product> items;
}
