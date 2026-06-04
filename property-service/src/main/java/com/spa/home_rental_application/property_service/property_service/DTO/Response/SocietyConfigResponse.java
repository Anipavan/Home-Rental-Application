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

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
