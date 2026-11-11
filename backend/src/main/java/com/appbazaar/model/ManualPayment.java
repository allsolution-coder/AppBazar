package com.appbazaar.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** A UPI (QR code) payment that the buyer reports and the admin verifies against the bank or UPI app. */
@Entity
@Getter @Setter @NoArgsConstructor
public class ManualPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User user;
    private String kind;                // fee or order
    @Column(length = 500)
    private String productIds;          // comma separated, for orders
    private int amountUsd;
    private long amountInr;
    @Column(unique = true, nullable = false)
    private String reference;           // short code shown in the UPI note
    @Column(unique = true)
    private String utr;                 // bank transaction id typed by the buyer
    private String status;              // PENDING, SUBMITTED, APPROVED, REJECTED
    @Column(length = 300)
    private String note;                // rejection reason
    private Instant createdAt;
    private Instant submittedAt;
    private Instant reviewedAt;
}
