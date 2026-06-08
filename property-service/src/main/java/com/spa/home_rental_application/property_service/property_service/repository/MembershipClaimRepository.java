package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Lookups for {@link MembershipClaim}.
 *
 * <p>Two leading-column indexes (on {@code (building_id, status)} and
 * {@code (user_id, status)}) cover the four hot queries:
 * <ul>
 *   <li>Owner widget — pending claims across the owner's buildings.</li>
 *   <li>Claimant's "my claims" — list a single user's own claims.</li>
 *   <li>Dedup — check whether a user already has a PENDING claim
 *       for the building they're trying to submit against.</li>
 *   <li>Resolution sweep — when an owner approves a maintainer
 *       claim, mark any other PENDING maintainer claims for the same
 *       building as REJECTED (only one maintainer at a time).</li>
 * </ul>
 */
@Repository
public interface MembershipClaimRepository extends JpaRepository<MembershipClaim, String> {

    List<MembershipClaim> findByBuildingIdAndStatus(String buildingId, Status status);

    List<MembershipClaim> findByBuildingIdInAndStatus(List<String> buildingIds, Status status);

    List<MembershipClaim> findByUserId(String userId);

    List<MembershipClaim> findByUserIdAndStatus(String userId, Status status);

    Optional<MembershipClaim> findFirstByBuildingIdAndUserIdAndStatus(
            String buildingId, String userId, Status status);

    /**
     * Claims of a given status against any building in the given
     * list, AND with dual-approval enabled. Used by the maintainer-
     * dashboard "pending requests for your society" widget — caller
     * supplies the buildings they're the current maintainer of, we
     * return the claims awaiting their decision.
     */
    List<MembershipClaim> findByBuildingIdInAndStatusAndRequiresDualApproval(
            List<String> buildingIds, Status status, Boolean requiresDualApproval);
}
