package com.spa.home_rental_application.kyc_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload to verify a PAN number against the provider's eKYC service.
 */
public record VerifyPanRequest(
        @NotBlank String userId,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "panNumber must match AAAAA9999A")
        String panNumber,

        @NotBlank String panHolderName
) {
}
