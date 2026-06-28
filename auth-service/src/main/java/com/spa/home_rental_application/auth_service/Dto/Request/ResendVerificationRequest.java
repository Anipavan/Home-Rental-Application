package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /auth/resend-verification}. The user typed
 * their email on the login page after seeing the
 * EMAIL_VERIFICATION_REQUIRED error and clicking "resend".
 */
public record ResendVerificationRequest(
        @NotBlank(message = "email is mandatory")
        @Email(message = "must be a valid email address")
        String email
) {}
