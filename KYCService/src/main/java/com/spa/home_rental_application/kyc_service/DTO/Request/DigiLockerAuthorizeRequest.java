package com.spa.home_rental_application.kyc_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Frontend payload to begin a DigiLocker OAuth flow. We don't ask for
 * Aadhaar/PAN here — DigiLocker is the source of truth and returns the
 * Aadhaar number itself in the signed eAadhaar XML.
 *
 * <p>{@code consentText} is mandatory per the DPDP Act 2023 and Aadhaar
 * Act 2016 §8 — we persist the consent string verbatim on the KYC
 * record so we can prove informed consent in a compliance audit.
 */
public record DigiLockerAuthorizeRequest(
        @NotBlank(message = "Explicit consent text is required (DPDP Act 2023)")
        String consentText
) {
}
