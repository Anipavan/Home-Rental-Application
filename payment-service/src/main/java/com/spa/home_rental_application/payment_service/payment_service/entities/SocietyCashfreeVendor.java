package com.spa.home_rental_application.payment_service.payment_service.entities;

import com.spa.home_rental_application.payment_service.payment_service.enums.CashfreeVendorStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One row per SOCIETY in the Cashfree Easy Split vendor lifecycle.
 * Sibling of {@link CashfreeVendor} — that one holds per-user vendors
 * (used for rent flow), this one holds per-society vendors (used for
 * maintenance flow so tenant maintenance money settles to the
 * society's own bank account, not the maintainer's personal one).
 *
 * <p>Owned by payment-service. Keyed by {@code building_id} for the
 * lookup convenience — every building has at most one society, and
 * every maintenance Payment carries {@code flatId} which we resolve
 * upstream via property-service Feign to the building id. The
 * {@code society_config_id} column is denormalised for audit but
 * not queried directly.
 *
 * <p>Cashfree's opaque vendor id is stored separately in
 * {@link #cashfreeVendorId} — we pass {@code "society_" + building_id}
 * as the vendor_id when creating (namespaced to avoid collision with
 * per-user vendor ids, which use the raw user auth id).
 */
@Entity
@Table(name = "society_cashfree_vendors", indexes = {
        @Index(name = "idx_soc_cf_vendors_status", columnList = "status"),
        @Index(name = "idx_soc_cf_vendors_cf_id",  columnList = "cashfree_vendor_id"),
        @Index(name = "idx_soc_cf_vendors_soc_id", columnList = "society_config_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocietyCashfreeVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** The building this society vendor represents. Unique — one
     *  vendor per building/society. */
    @Column(name = "building_id", nullable = false, unique = true, length = 64)
    private String buildingId;

    /** society_config.id from property-service — denormalised for
     *  audit correlation, not queried directly. Nullable in case
     *  the config id isn't known at registration time (rare). */
    @Column(name = "society_config_id", length = 64)
    private String societyConfigId;

    /** Cashfree's opaque vendor id; format {@code society_{buildingId}}.
     *  Nullable until first create succeeds. */
    @Column(name = "cashfree_vendor_id", length = 128)
    private String cashfreeVendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CashfreeVendorStatus status = CashfreeVendorStatus.PENDING_KYC;

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    @Column(name = "bank_account_holder", length = 200)
    private String bankAccountHolder;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = CashfreeVendorStatus.PENDING_KYC;
        if (attemptCount == null) attemptCount = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
