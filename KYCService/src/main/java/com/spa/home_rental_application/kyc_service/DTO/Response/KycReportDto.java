package com.spa.home_rental_application.kyc_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Compliance-grade report consumed by the owner dashboard / audit trail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KycReportDto(
        String userId,
        String kycProvider,
        String status,
        Boolean aadhaarVerified,
        Boolean panVerified,
        Boolean digilockerLinked,
        Boolean consentRecorded,
        String confidenceLevel,    // HIGH | MEDIUM | LOW (derived)
        LocalDateTime verifiedAt,
        LocalDateTime generatedAt
) {
}
