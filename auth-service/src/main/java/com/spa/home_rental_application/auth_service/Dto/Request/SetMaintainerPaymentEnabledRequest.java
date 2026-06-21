package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /admin/settings/maintainer-payment-enabled}.
 * Single boolean — explicit DTO so a future "reason" or "scheduled
 * at" field doesn't break the wire shape.
 */
public record SetMaintainerPaymentEnabledRequest(
        @NotNull(message = "enabled is mandatory")
        Boolean enabled
) {}
