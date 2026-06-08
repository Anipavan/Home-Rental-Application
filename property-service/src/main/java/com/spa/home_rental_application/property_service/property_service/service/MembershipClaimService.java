package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.DecideMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MembershipClaimResponse;

import java.util.List;

/**
 * Self-service membership claims. Users submit one via the signup
 * page; owners approve / reject from their dashboard. See the V6
 * migration for design rationale.
 */
public interface MembershipClaimService {

    /**
     * Create a new claim from the calling user. Validates that the
     * building exists, that the user does not already have a PENDING
     * claim for the same building, and that {@code claimedFlatNumber}
     * is set when {@code requestedRole=RESIDENT}.
     */
    MembershipClaimResponse createClaim(CreateMembershipClaimRequest req);

    /**
     * List PENDING claims across every building the caller owns. The
     * owner dashboard widget consumes this directly — empty list if
     * the caller owns no buildings or has no pending requests.
     */
    List<MembershipClaimResponse> listPendingForOwner();

    /** List every claim (any status) the calling user submitted. */
    List<MembershipClaimResponse> listMine();

    /**
     * Dual-approval claims awaiting the calling user's decision as the
     * <em>current maintainer</em> of the affected building. Empty list
     * if the caller maintains no buildings or has no pending dual-
     * approval requests against any of them.
     */
    List<MembershipClaimResponse> listPendingForCurrentMaintainer();

    /**
     * Approve a pending claim. Behaviour:
     *
     * <ul>
     *   <li><b>Single-party</b> (the building has no current maintainer
     *       OR the claim is RESIDENT): caller must be the building owner.
     *       MAINTAINER claims swap {@code society_config.maintainer_user_id}
     *       and bump the claimant's role; RESIDENT claims bind the
     *       claimant to the flat they named.</li>
     *   <li><b>Dual-party</b> (MAINTAINER claim against a building with
     *       an active maintainer): caller can be either the owner or
     *       the current maintainer. The matching {@code *_decided_at}
     *       column is stamped. The swap only fires once BOTH sides
     *       have approved.</li>
     * </ul>
     */
    MembershipClaimResponse approveClaim(String claimId, DecideMembershipClaimRequest req);

    /** Reject a pending claim. Either party (owner or current maintainer)
     *  can reject a dual-approval claim — first to reject kills it. */
    MembershipClaimResponse rejectClaim(String claimId, DecideMembershipClaimRequest req);

    /**
     * The claimant cancels their own PENDING claim. Does nothing if
     * the claim has already been decided (the owner moved first).
     */
    MembershipClaimResponse withdrawClaim(String claimId);
}
