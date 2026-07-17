package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * V15 — "Who lives in this flat for maintenance-billing purposes."
 *
 * <p>Orthogonal to {@link Flat#getTenantId()}, which is the RENTAL
 * tenant (there's a lease, rent flows to the owner). A society member
 * may be the rental tenant, the owner-occupier, a family member, or
 * anyone else the maintainer has approved as a resident. Maintenance
 * dues are billed against every active row in this table; rent is
 * billed only against {@code Flat.tenantId} + an active lease.
 *
 * <p>The composite (flatId, userId) key lets us upsert idempotently
 * — a resident who moves out and back in reactivates the same row
 * (setting {@code isActive=true}) instead of leaving orphans behind.
 */
@Entity
@Table(name = "flat_society_membership", indexes = {
        @Index(name = "idx_fsm_user", columnList = "user_id"),
        @Index(name = "idx_fsm_flat_active", columnList = "flat_id,is_active")
})
@IdClass(FlatSocietyMembership.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlatSocietyMembership {

    @Id
    @Column(name = "flat_id", nullable = false, length = 64)
    private String flatId;

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** authUserId of the actor who approved this membership — owner
     *  approving a RESIDENT claim, or the assign-flat call's caller
     *  when the maintenance row is auto-created alongside a lease.
     *  Nullable so a manual DB backfill can create rows without a
     *  known approver. */
    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    /** Soft-delete flag. Flipped to 0 by the vacate flow so the row
     *  survives as an audit trail of past residents; the maintenance
     *  billing scan only reads {@code is_active = 1} rows. */
    @Column(name = "is_active", nullable = false,
            columnDefinition = "NUMBER(1) DEFAULT 1")
    @Builder.Default
    private Boolean isActive = true;

    /** JPA composite-key holder. Kept as a nested static class so the
     *  entity + its key live in one file — the two are tightly
     *  coupled and having them apart would just create navigation
     *  overhead. */
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String flatId;
        private String userId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK other)) return false;
            return Objects.equals(flatId, other.flatId)
                    && Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flatId, userId);
        }
    }
}
