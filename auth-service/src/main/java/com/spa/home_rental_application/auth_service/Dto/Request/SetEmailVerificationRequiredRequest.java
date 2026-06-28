package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /admin/settings/email-verification-required}.
 * Single boolean — matches the {@link SetMaintainerPaymentEnabledRequest}
 * shape for consistency.
 */
public record SetEmailVerificationRequiredRequest(
        @NotNull(message = "required is mandatory")
        Boolean required
) {}
