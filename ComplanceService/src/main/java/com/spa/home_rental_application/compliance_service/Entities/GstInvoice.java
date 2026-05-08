package com.spa.home_rental_application.compliance_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GST invoice record per payment. One invoice per paymentId
 * (UNIQUE constraint enforced at the column level).
 * <p>
 * Indian rule of thumb: GST on residential rentals applies only when the
 * landlord's annualised rent income exceeds ₹20 lakhs. Below that the
 * row still exists but {@code gstApplicable=false} and {@code gstAmount=0}.
 */
@Entity
@Table(name = "gst_invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gst_payment", columnNames = "payment_id"),
                @UniqueConstraint(name = "uk_gst_invoice_no", columnNames = "invoice_number")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GstInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "invoice_number", length = 50, nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "rent_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal rentAmount;

    @Column(name = "gst_applicable", nullable = false)
    @Builder.Default
    private Boolean gstApplicable = false;

    @Column(name = "gst_rate_percent", precision = 5, scale = 2)
    private BigDecimal gstRatePercent;

    @Column(name = "gst_amount", precision = 12, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "sent_via_whatsapp", nullable = false)
    @Builder.Default
    private Boolean sentViaWhatsapp = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
