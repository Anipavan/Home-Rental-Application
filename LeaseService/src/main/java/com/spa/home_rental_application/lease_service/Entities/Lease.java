package com.spa.home_rental_application.lease_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persistent lease record. {@code lease_number} is the human-readable id
 * we hand to tenants/owners; {@code id} is the system UUID used by APIs.
 */
@Entity
@Table(name = "leases",
        uniqueConstraints = @UniqueConstraint(name = "uk_lease_number", columnNames = "lease_number"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "flat_id", nullable = false)
    private String flatId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "lease_number", length = 50, nullable = false, unique = true)
    private String leaseNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "rent_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal rentAmount;

    @Column(name = "security_deposit", precision = 12, scale = 2)
    private BigDecimal securityDeposit;

    @Column(name = "rent_increment_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal rentIncrementPercent = new BigDecimal("5.00");

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "DRAFT";          // DRAFT | ACTIVE | EXPIRED | TERMINATED

    @Column(name = "rera_agreement_number", length = 100)
    private String reraAgreementNumber;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "digital_signature_status", length = 20)
    @Builder.Default
    private String digitalSignatureStatus = "PENDING";   // PENDING | SIGNED | REJECTED

    @Column(name = "ai_renewal_probability", precision = 5, scale = 4)
    private BigDecimal aiRenewalProbability;

    @Column(name = "expiry_warning_sent_at")
    private LocalDateTime expiryWarningSentAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "termination_reason", length = 50)
    private String terminationReason;

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
