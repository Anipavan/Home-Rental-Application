package com.spa.home_rental_application.payment_service.payment_service.entities;

import com.spa.home_rental_application.payment_service.payment_service.enums.CashfreeVendorStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One row per owner in the Cashfree Easy Split vendor lifecycle.
 *
 * <p>Owned by payment-service. Keyed by {@code user_id} (the owner's
 * auth id) so cross-service references stay stable across restarts.
 * The Cashfree side of the id is stored separately in
 * {@link #cashfreeVendorId} — usually equal to {@code user_id} because
 * we pass it as the vendor_id when creating, but kept as a distinct
 * column in case Cashfree ever returns a different opaque id.
 */
@Entity
@Table(name = "cashfree_vendors", indexes = {
        @Index(name = "idx_cf_vendors_status", columnList = "status"),
        @Index(name = "idx_cf_vendors_cf_id",  columnList = "cashfree_vendor_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashfreeVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    /** Cashfree's opaque vendor id; usually == userId. Nullable until first create. */
    @Column(name = "cashfree_vendor_id", length = 64)
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
