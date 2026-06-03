package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import lombok.Builder;

/**
 * Response from {@code POST /society/{buildingId}/maintainer/promote-tenant}.
 *
 * <p>Echoes the credentials the owner just set so the UI can render a
 * "share these with the maintainer" panel. The temp password is
 * returned in plain text intentionally — the owner needs to copy it
 * to a WhatsApp message. No-auto-email policy stays in place; password
 * hygiene is the owner's responsibility.
 *
 * <p>Idempotency is best-effort: re-running the call with the same
 * tenantUserId and a fresh password will simply reset the password
 * again. The role flip is a no-op if already MAINTAINER; the
 * maintainerUserId link is a no-op if already set to the same id.
 */
@Builder
public record PromoteTenantResponse(
        String tenantUserId,
        String userName,
        String temporaryPassword,
        /** Pre-formatted human-readable summary; safe to render verbatim. */
        String message
) {
}
