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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
