package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import lombok.Builder;

/**
 * One row in the "Pick a tenant to promote" dropdown the owner uses
 * when assigning a maintainer to a building. The flow is:
 *
 * <ol>
 *   <li>Owner opens the AssignMaintainerDialog on the society page.</li>
 *   <li>UI fetches {@code GET /society/{buildingId}/eligible-maintainers}.</li>
 *   <li>Backend returns every currently-assigned tenant in any flat in
 *       the building — these are the residents the owner can promote
 *       without creating a brand-new account.</li>
 *   <li>Owner picks one + types/auto-generates a temp password →
 *       {@code POST /society/{buildingId}/maintainer/promote-tenant}.</li>
 * </ol>
 *
 * <p>This response is owner / admin only — the eligible-maintainers
 * route enforces it at the service layer. Tenant names + contact info
 * are not sensitive between residents of the same building (they share
 * a building, an owner already has their phone for rent reminders),
 * but we still keep this endpoint owner-only so the building's tenant
 * roster doesn't leak to other roles.
 *
 * <p>{@code displayName} is a pre-joined "Flat 101 — Ramesh K." string
 * the dropdown renders verbatim. The frontend doesn't need to know how
 * to format flat-number + tenant-name combinations — that's a backend
 * concern (and lets us change the format without a frontend release).
 */
@Builder
public record EligibleMaintainerResponse(
        /** authUserId of the tenant — what goes into the promote-tenant POST body. */
        String tenantUserId,

        String flatId,
        String flatNumber,

        /** First + last name when user-service has them, else userName fallback. */
        String tenantName,

        /** Pre-formatted "Flat 101 — Ramesh K." string the UI renders verbatim. */
        String displayName,

        String email,
        String phone
) {
}
