package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /auth/internal/registration/activate/{authUserId}}.
 * The {@code paymentId} is carried for audit purposes — the activation
 * itself doesn't re-verify the payment (that's payment-service's job
 * before it issues this call), but every audit row gets the paymentId
 * stamped on it for cross-service traceability.
 */
public record ActivateRegistrationRequest(
        @NotBlank(message = "paymentId is mandatory")
        String paymentId
) {}
