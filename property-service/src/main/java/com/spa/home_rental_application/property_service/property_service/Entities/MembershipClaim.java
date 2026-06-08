package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Self-service membership claim. A user registers on the public
 * signup page, picks a building, and POSTs a claim against it. The
 * building owner sees a pending request on their dashboard and
 * approves or rejects. See {@code V6__membership_claims.sql} for the
 * full design write-up.
 *
 * <p>Two flavours:
 * <ul>
 *   <li>{@code MAINTAINER} — claim to run this society. On approval,
 *       property-service updates {@code society_config.maintainer_user_id}
 *       (REPLACING any existing maintainer) and calls auth-service to
 *       escalate the claimant's role to MAINTAINER.</li>
 *   <li>{@code RESIDENT} — claim to be the tenant of a specific flat
 *       in this building. On approval, the user is bound to that flat
 *       via the existing flat-tenant assignment write path. Role stays
 *       TENANT.</li>
 * </ul>
 *
 * <p>We deliberately do not foreign-key {@code user_id} to a user
 * table — users live in auth-service / user-service, and cross-service
 * FK coupling would tie our migrations together. We treat user_id as
 * an opaque identifier and validate it at write time through the
 * existing {@code AuthClient} feign.
 */
@Entity
@Table(
        name = "membership_claims",
        indexes = {
                @Index(name = "idx_claims_building_status", columnList = "building_id, status"),
                @Index(name = "idx_claims_user_status", columnList = "user_id, status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipClaim {

    /**
     * MAINTAINER  — applicant wants to manage the society's books.
     * RESIDENT    — applicant is the tenant of a specific flat.
     * FLAT_OWNER  — applicant owns a specific flat (V8). On approval
     *               we set {@code flat.flatOwnerId = userId} and, if
     *               the flat is currently vacant, also set
     *               {@code tenantId = userId} so the new owner is
     *               their own occupant by default (owner-occupier).
     */
    public enum RequestedRole { MAINTAINER, RESIDENT, FLAT_OWNER }
    public enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN }

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "building_id", length = 36, nullable = false)
    private String buildingId;

    /** authUserId of the claimant. Treated as an opaque string so the
     *  cross-service boundary stays clean. */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", length = 16, nullable = false)
    private RequestedRole requestedRole;

    /** Flat number the claimant says they live in. Required by the
     *  service layer for RESIDENT claims (used to look up the Flat
     *  row at approval); recorded but not used at approval for
     *  MAINTAINER claims. */
    @Column(name = "claimed_flat_number", length = 32)
    private String claimedFlatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    /** Optional free-text from the claimant ("I am tenant of flat 201
     *  since June 2024") — gives the owner context for approve/reject. */
    @Column(name = "applicant_note", length = 500)
    private String applicantNote;

    /** Owner's optional decision note — shown back to the claimant. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /** authUserId of whoever closed the loop (final approver / rejecter).
     *  Null on PENDING and AWAITING_BOTH. */
    @Column(name = "decided_by_user_id", length = 64)
    private String decidedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Final-decision timestamp. Null while still PENDING or
     *  AWAITING_BOTH. Equals owner_decided_at OR maintainer_decided_at
     *  depending on which side closed the loop. */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /* ─── Dual-approval (V7) ───
     * When a MAINTAINER claim targets a building that already has an
     * active maintainer, both the OWNER and the CURRENT MAINTAINER
     * must approve. We track each side's decision separately so the
     * UI can show "1 of 2 approvals" status mid-flight.
     */

    /** True when the claim requires both owner and current-maintainer
     *  approval. Set at create time by the service layer based on
     *  whether a current maintainer exists. Stored as a column (not
     *  derived) so the dual-approval requirement stays stable even if
     *  the maintainer changes mid-flight. */
    @Column(name = "requires_dual_approval", nullable = false)
    @Builder.Default
    private Boolean requiresDualApproval = false;

    @Column(name = "owner_decided_at")
    private LocalDateTime ownerDecidedAt;

    @Column(name = "owner_decided_by_user_id", length = 64)
    private String ownerDecidedByUserId;

    @Column(name = "maintainer_decided_at")
    private LocalDateTime maintainerDecidedAt;

    @Column(name = "maintainer_decided_by_user_id", length = 64)
    private String maintainerDecidedByUserId;
}
