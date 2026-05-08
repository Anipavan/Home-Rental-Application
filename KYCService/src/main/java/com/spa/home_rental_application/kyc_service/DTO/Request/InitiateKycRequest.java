package com.spa.home_rental_application.kyc_service.DTO.Request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload to start an Aadhaar / DigiLocker KYC flow with the configured
 * provider (Digio by default).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitiateKycRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{12}$", message = "aadhaarNumber must be 12 digits")
        String aadhaarNumber,

        @Size(max = 200)
        String fullName,

        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "panNumber must match AAAAA9999A")
        String panNumber,

        @NotBlank(message = "consent is mandatory under DPDP Act")
        String consentText,

        Boolean linkDigilocker
) {
}
