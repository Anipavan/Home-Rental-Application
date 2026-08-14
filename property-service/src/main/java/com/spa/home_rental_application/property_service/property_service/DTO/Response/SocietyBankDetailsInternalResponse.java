package com.spa.home_rental_application.property_service.property_service.DTO.Response;

/**
 * Internal-only bank + KYC dump for the SOCIETY, consumed by
 * payment-service's Feign client to register a Cashfree Easy Split
 * vendor keyed on the society (not the maintainer user).
 *
 * <p>Contains the FULL account number (unmasked) — exposed only to
 * other services on the internal call plane, never to browser
 * clients. The regular {@code SocietyConfigResponse} keeps its
 * existing display-only shape for browser consumers.
 */
public record SocietyBankDetailsInternalResponse(
        String buildingId,
        String societyConfigId,
        String maintainerUserId,
        String societyDisplayName,

        /* Bank */
        String accountNumber,
        String ifscCode,
        String accountHolder,
        String upiId,

        /* KYC */
        String panNumber,
        String contactPhone,
        String contactEmail,
        String businessType
) {}
