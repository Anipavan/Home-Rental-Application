package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lease agreement between an owner and tenant for a specific flat.
 * Created automatically the moment an owner assigns a flat to a tenant.
 *
 * Lifecycle: PENDING_SIGNATURE -> SIGNED or REJECTED.
 *
 * Signature is stored as a base64-encoded PNG of the canvas drawing.
 * Reasonable size (signatures are typically &lt; 50 KB) so we keep them
 * in-row rather than uploading to a separate blob store.
 */
@Entity
@Table(name = "lease_agreements", indexes = {
        @Index(name = "idx_agreement_tenant", columnList = "tenant_id"),
        @Index(name = "idx_agreement_flat",   columnList = "flat_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Agreement {

    @Id
    private String id;

    @Column(name = "flat_id",     nullable = false) private String flatId;
    @Column(name = "building_id", nullable = false) private String buildingId;
    @Column(name = "tenant_id",   nullable = false) private String tenantId;
    @Column(name = "owner_id",    nullable = false) private String ownerId;

    @Column(name = "rent_amount",      precision = 12, scale = 2) private BigDecimal rentAmount;
    @Column(name = "lease_start_date") private LocalDate leaseStartDate;
    @Column(name = "lease_end_date")   private LocalDate leaseEndDate;

    @Column(name = "terms", length = 4000)
    private String terms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status;

    /** Base64 PNG of the tenant's signed signature. Null until SIGNED. */
    @Lob
    @Column(name = "signature_data")
    private String signatureData;

    @Column(name = "signed_at")     private LocalDateTime signedAt;
    @Column(name = "rejected_at")   private LocalDateTime rejectedAt;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public enum Status { PENDING_SIGNATURE, SIGNED, REJECTED }
}
