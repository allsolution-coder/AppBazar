package com.appbazaar.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;
    private int price;
    @Column(length = 2000)
    private String description;
    private double rating;
    private Integer reviewCount = 0;
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;
    private String fileName;
    private String storedFileName;
    private Long fileSize;
    private String techStack;
    private String college;
    @ManyToOne(optional = false)
    private User seller;
}
