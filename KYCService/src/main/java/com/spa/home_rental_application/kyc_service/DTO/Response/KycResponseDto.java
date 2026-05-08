package com.spa.home_rental_application.kyc_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outward representation of a KYC record. Aadhaar number is never echoed —
 * only the last 4 digits if available.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KycResponseDto(
        String id,
        String userId,
        String kycProvider,
        String verificationStatus,
        Boolean aadhaarVerified,
        Boolean panVerified,
        String panMasked,
        BigDecimal faceMatchScore,
        Boolean digilockerLinked,
        Boolean consentRecorded,
        String kycReferenceId,
        String failureReason,
        String failureCode,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
