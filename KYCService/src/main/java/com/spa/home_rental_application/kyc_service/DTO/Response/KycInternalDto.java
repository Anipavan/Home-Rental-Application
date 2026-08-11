package com.spa.home_rental_application.kyc_service.DTO.Response;

/**
 * Internal-only projection with the RAW PAN — served by
 * {@code GET /kyc/internal/{userId}}. Consumed by payment-service
 * when registering an owner with Cashfree Easy Split; Cashfree needs
 * the raw PAN for KYC linkage.
 *
 * <p>Never exposed via a public route. Path lives under
 * {@code /kyc/internal/**} and is protected by the shared
 * {@code GatewayAuthFilter} — direct hits without a valid gateway
 * HMAC are rejected before this DTO is populated.
 *
 * <p>{@link #verified} lets the caller enforce "vendor registration
 * only when KYC is fully done" without a second call.
 */
public record KycInternalDto(
        String userId,
        String panNumber,          // RAW — for Cashfree kyc_details.pan
        String panHolderName,      // legal name to send as vendor "name"
        Boolean verified,          // true when verification_status == VERIFIED
        String verificationStatus  // full string for auditability
) {}
