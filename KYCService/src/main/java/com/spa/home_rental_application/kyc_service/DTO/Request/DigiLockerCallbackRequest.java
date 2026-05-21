package com.spa.home_rental_application.kyc_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload the frontend posts back to us after DigiLocker has redirected
 * the user to our callback URL. {@code code} is the OAuth 2.0
 * authorization code we exchange for an access_token, {@code state} is
 * the CSRF token we persisted on initiate and now cross-check.
 *
 * <p>Neither value is ever persisted in plain text — {@code code} is
 * one-shot (DigiLocker invalidates it after use) and {@code state} is
 * matched against the DB and then cleared.
 */
public record DigiLockerCallbackRequest(
        @NotBlank String code,
        @NotBlank String state
) {
}
