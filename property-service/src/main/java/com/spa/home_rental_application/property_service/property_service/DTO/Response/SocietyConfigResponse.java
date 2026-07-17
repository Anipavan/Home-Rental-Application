package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Owner / maintainer-visible config response. The
 * {@code publicViewToken} is returned ONLY to authorised callers
 * (owner / maintainer / admin) so they can copy the shareable URL —
 * the public ledger view never sees this response.
 */
@Builder
public record SocietyConfigResponse(
        String id,
        String buildingId,
        Integer monthlyDueDay,
        BigDecimal defaultPerFlatAmount,
        String maintainerUserId,
        String publicViewToken,
        String publicViewUrl,
        String societyDisplayName,

        /* ─── Collection bank / UPI (nullable) ───
         * Surfaced to authorised callers (owner / maintainer /
         * tenant of the building) so the frontend can render the
         * QR + bank-info panel on the Pay flow. NEVER returned in
         * the public-ledger response.
         */
        String upiId,
        String payeeName,
        String accountNumber,
        String ifscCode,

        /* ─── Bank-config health flag (V16) ───
         * Non-null timestamp = at least one tenant has reported the
         * society's UPI as broken via the "This UPI isn't working"
         * button on the direct-UPI pay page. Maintainer dashboard
         * renders a warning banner and prompts them to re-check
         * their UPI / payee details. Auto-cleared when the maintainer
         * next saves fresh bank details via the bank panel.
         */
        LocalDateTime bankConfigFlaggedAt,
        Integer bankConfigFlagReports,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
