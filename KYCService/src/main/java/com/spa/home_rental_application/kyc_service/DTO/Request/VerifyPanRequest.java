package com.spa.home_rental_application.kyc_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload to verify a PAN number against the provider's eKYC service.
 *
 * <p>{@code dateOfBirth} is required by Sandbox.co.in's PAN verification
 * endpoint (and most NSDL upstreams) as a second-factor match — the
 * provider hashes (PAN + DOB) and rejects the request if DOB doesn't
 * match what NSDL has on file. Accepted format is ISO {@code yyyy-MM-dd}
 * here; the provider layer converts to whatever shape the upstream
 * needs (Sandbox wants {@code dd/MM/yyyy}).
 *
 * <p>{@link Pattern} on DOB enforces the ISO shape early so the
 * frontend gets a clean 400 before we burn a paid provider call on a
 * malformed date.
 */
public record VerifyPanRequest(
        @NotBlank String userId,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "panNumber must match AAAAA9999A")
        String panNumber,

        @NotBlank String panHolderName,

        @NotBlank(message = "dateOfBirth is mandatory")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                message = "dateOfBirth must be in yyyy-MM-dd format")
        String dateOfBirth
) {
}
