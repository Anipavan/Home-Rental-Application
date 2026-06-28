package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /auth/verify-email}. The token is the raw
 * URL-safe string from the magic link the user received in their
 * verification email.
 */
public record VerifyEmailRequest(
        @NotBlank(message = "token is mandatory")
        @Size(max = 64, message = "token is malformed")
        String token
) {}
