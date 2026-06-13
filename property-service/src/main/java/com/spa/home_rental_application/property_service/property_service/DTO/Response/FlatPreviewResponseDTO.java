package com.spa.home_rental_application.property_service.property_service.DTO.Response;

/**
 * Lightweight, PUBLIC-safe preview of a flat. Returned by
 * {@code GET /properties/flats/preview?buildingId=..&flatNumber=..}.
 *
 * <p>Used by the maintainer-signup form to validate, before the
 * /auth/register call fires, that the building+flat the applicant
 * claims to live in actually exists AND is currently occupied.
 * The form refuses to submit unless both flags are true — keeps
 * fraudulent maintainer applications out of the owner's pending-
 * claims queue and avoids the "orphan account whose only claim
 * was rejected" situation.
 *
 * <p>Deliberately omits flatId, tenantId, ownerId, rent, area, and
 * everything else that could be used to enumerate tenants — this
 * endpoint is open to anonymous callers (gateway public-paths
 * already opens {@code GET /properties/flats/**}), so the response
 * shape is the privacy surface area. {@code exists} + {@code occupied}
 * are the minimum needed for the signup-form gate and leak nothing
 * the building's address sign wouldn't also reveal to a passer-by.
 *
 * <p>{@code exists=false} → no flat with that number in the building.
 * {@code exists=true, occupied=false} → flat is vacant; applicant
 * isn't a current resident, so a maintainer claim from them is
 * rejected at the form level.
 * {@code exists=true, occupied=true} → green light.
 *
 * @param exists   whether a non-deleted flat with that number exists
 *                 in the given building
 * @param occupied whether that flat currently has a tenant assigned
 *                 ({@code is_occupied = true} AND tenantId is non-null).
 *                 Always false when {@code exists} is false.
 */
public record FlatPreviewResponseDTO(
        boolean exists,
        boolean occupied
) {
    public static FlatPreviewResponseDTO notFound() {
        return new FlatPreviewResponseDTO(false, false);
    }
    public static FlatPreviewResponseDTO of(boolean occupied) {
        return new FlatPreviewResponseDTO(true, occupied);
    }
}
