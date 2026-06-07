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
     * Approve a pending claim. MAINTAINER claims update the building's
     * {@code society_config.maintainer_user_id} (replacing any existing
     * maintainer) and bump the claimant's role to MAINTAINER via
     * auth-service. RESIDENT claims bind the claimant to the flat they
     * named via property-service's existing flat-assignment write path.
     */
    MembershipClaimResponse approveClaim(String claimId, DecideMembershipClaimRequest req);

    /** Reject a pending claim. No side-effects beyond the row update. */
    MembershipClaimResponse rejectClaim(String claimId, DecideMembershipClaimRequest req);

    /**
     * The claimant cancels their own PENDING claim. Does nothing if
     * the claim has already been decided (the owner moved first).
     */
    MembershipClaimResponse withdrawClaim(String claimId);
}
