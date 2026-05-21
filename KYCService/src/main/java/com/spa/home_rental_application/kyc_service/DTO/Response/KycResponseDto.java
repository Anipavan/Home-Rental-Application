package com.spa.home_rental_application.kyc_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outward representation of a KYC record. Aadhaar number is never echoed —
 * only the last 4 digits if available.
 *
 * <p>The trio at the bottom ({@code aadhaarLast4}, {@code dateOfBirth},
 * {@code panHolderName}) is populated by the DigiLocker flow. They're
 * safe to show to the verified user themselves — the same fragments
 * UIDAI permits banks and UPI apps to display.
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
        LocalDateTime updatedAt,
        String aadhaarLast4,
        String dateOfBirth,
        String nameOnAadhaar
) {
}
