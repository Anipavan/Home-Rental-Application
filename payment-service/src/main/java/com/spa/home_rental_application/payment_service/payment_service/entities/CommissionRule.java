package com.spa.home_rental_application.payment_service.payment_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single commission rule.
 *
 * <p>The row with {@link #ownerId} == {@code null} is the platform-wide
 * <em>global default</em>. Rows with a populated {@code ownerId} are
 * per-owner overrides that take precedence at compute time.
 *
 * <p>Rates are stored in basis points ({@code rateBps}) so admin-entered
 * percentages persist without floating-point drift — 200 bps = 2.00 %.
 * A {@code rateBps == 0} row is a valid "free forever" rule.
 */
@Entity
@Table(name = "commission_rules", indexes = {
        @Index(name = "idx_commission_rules_owner", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Null → global default. Non-null → per-owner override. */
    @Column(name = "owner_id", length = 64)
    private String ownerId;

    /**
     * Percentage in basis points. 0 → 0 %, 200 → 2.00 %, 250 → 2.50 %.
     * Bounded [0, 10000] by a DB-level CHECK constraint.
     */
    @Column(name = "rate_bps", nullable = false)
    private Integer rateBps;

    /** Auth user id of the admin who last edited. Nullable for the seeded default. */
    @Column(name = "updated_by_admin_id", length = 64)
    private String updatedByAdminId;

    /** Free-text audit hint the admin attaches when creating an override. */
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
