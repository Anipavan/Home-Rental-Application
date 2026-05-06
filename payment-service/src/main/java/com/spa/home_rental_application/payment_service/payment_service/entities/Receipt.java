package com.spa.home_rental_application.payment_service.payment_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "receipts", indexes = {
        @Index(name = "idx_receipts_number", columnList = "receipt_number", unique = true),
        @Index(name = "idx_receipts_payment", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @Column(name = "generated_date", nullable = false, updatable = false)
    private Instant generatedDate;

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl;

    @PrePersist
    void prePersist() {
        if (generatedDate == null) generatedDate = Instant.now();
    }
}
