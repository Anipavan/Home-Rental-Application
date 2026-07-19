package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import lombok.Builder;

/**
 * One row in the "Pick a person to make maintainer" dropdown the owner
 * uses when assigning a maintainer to a building. Merges TWO
 * populations:
 *
 * <ol>
 *   <li><b>TENANT</b> — a person already assigned to a flat in this
 *       building. Owner-driven promote: sets a temporary password via
 *       {@code POST /society/{buildingId}/maintainer/promote-tenant},
 *       backed by auth-service's dual-credential model so the person's
 *       tenant login still works.</li>
 *   <li><b>SELF_REGISTERED</b> — a person who submitted a MAINTAINER
 *       {@code MembershipClaim} through the /setup-society signup and
 *       is waiting for owner approval. One-click approval via
 *       {@code PUT /society/claims/{claimId}/approve} — they already
 *       chose their own password at signup.</li>
 * </ol>
 *
 * <p>The frontend distinguishes the two via {@link #source} and hides
 * the temp-password field for SELF_REGISTERED rows. {@link #claimId}
 * is populated for SELF_REGISTERED only (null for TENANT) so the
 * frontend knows which endpoint to call.
 *
 * <p>Owner / admin only — the eligible-maintainers route enforces it
 * at the service layer.
 */
@Builder
public record EligibleMaintainerResponse(
        /** authUserId of the person — what goes into the promote-tenant
         *  POST body OR identifies the claim submitter. */
        String tenantUserId,

        /** Where this candidate came from. Frontend uses this to pick
         *  which endpoint to call on submit. */
        Source source,

        /** Populated when {@link #source} == SELF_REGISTERED — the id
         *  of the MembershipClaim to approve. Null for TENANT rows. */
        String claimId,

        /** Populated when {@link #source} == TENANT — the flat they
         *  currently occupy. Null for SELF_REGISTERED rows (their
         *  claim carries claimedFlatNumber, no flat_id yet). */
        String flatId,

        String flatNumber,

        /** First + last name when user-service has them, else userName fallback. */
        String tenantName,

        /** Pre-formatted "Flat 101 — Ramesh K." string the UI renders verbatim. */
        String displayName,

        String email,
        String phone
) {
    public enum Source { TENANT, SELF_REGISTERED }
}
