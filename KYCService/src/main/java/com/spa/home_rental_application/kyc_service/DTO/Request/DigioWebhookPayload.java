package com.spa.home_rental_application.kyc_service.DTO.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Minimal Digio webhook payload — subset of fields the platform documents.
 * Unknown fields are silently ignored so a Digio schema update doesn't break us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DigioWebhookPayload(
        String referenceId,        // our kyc_reference_id we sent on initiate
        String userId,
        String status,             // SUCCESS | FAILURE | PENDING
        String aadhaarLast4,
        String panNumber,
        String panHolderName,
        BigDecimal faceMatchScore,
        Boolean digilockerLinked,
        String failureCode,
        String failureReason
) {
}
