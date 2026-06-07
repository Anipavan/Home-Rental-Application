package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.RequestedRole;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.Status;

import java.time.LocalDateTime;

/**
 * Response projection for membership claim queries (owner widget,
 * claimant's "my claims" view, post-create echo). Enriches the raw
 * row with denormalised display fields the frontend would otherwise
 * have to round-trip for:
 *
 * <ul>
 *   <li>{@code buildingName} / {@code buildingCity} — shown on the
 *       claimant's "waiting for approval" screen and on the owner's
 *       pending-requests list.</li>
 *   <li>{@code applicantName} / {@code applicantEmail} — shown on the
 *       owner's pending-requests list so they recognise who they're
 *       approving. Looked up via the user-service feign at response
 *       build time; null if the lookup fails (the owner can still
 *       approve/reject).</li>
 * </ul>
 */
public record MembershipClaimResponse(
        String id,
        String buildingId,
        String buildingName,
        String buildingCity,
        String userId,
        String applicantName,
        String applicantEmail,
        RequestedRole requestedRole,
        String claimedFlatNumber,
        Status status,
        String applicantNote,
        String decisionNote,
        String decidedByUserId,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
    /**
     * Build a response from the row + the pre-fetched display fields.
     * The service layer batches the user/building lookups so we don't
     * issue N+1 round-trips when listing pending claims.
     */
    public static MembershipClaimResponse of(
            MembershipClaim c,
            String buildingName,
            String buildingCity,
            String applicantName,
            String applicantEmail) {
        return new MembershipClaimResponse(
                c.getId(),
                c.getBuildingId(),
                buildingName,
                buildingCity,
                c.getUserId(),
                applicantName,
                applicantEmail,
                c.getRequestedRole(),
                c.getClaimedFlatNumber(),
                c.getStatus(),
                c.getApplicantNote(),
                c.getDecisionNote(),
                c.getDecidedByUserId(),
                c.getCreatedAt(),
                c.getDecidedAt()
        );
    }
}
