package com.spa.home_rental_application.payment_service.payment_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_number", columnList = "invoice_number", unique = true),
        @Index(name = "idx_invoices_payment", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "generated_date", nullable = false, updatable = false)
    private Instant generatedDate;

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl;

    @PrePersist
    void prePersist() {
        if (generatedDate == null) generatedDate = Instant.now();
    }
}
