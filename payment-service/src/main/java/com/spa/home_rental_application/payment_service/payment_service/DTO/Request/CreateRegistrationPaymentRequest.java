package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body of {@code POST /payments/registration/create-pending}.
 * Called by auth-service via Feign during {@code /auth/register/pending}.
 *
 * <p>{@code payerAuthUserId} is the freshly-created (disabled) auth
 * row's id; payment-service stores it on the Payment row so the later
 * /verify call knows which user's enabled flag to flip back on.
 * {@code amountInr} is the fee resolved on the auth-service side
 * from {@code app.maintainer-registration.fee-inr}.
 */
public record CreateRegistrationPaymentRequest(
        @NotBlank(message = "payerAuthUserId is mandatory")
        String payerAuthUserId,

        @NotNull(message = "amountInr is mandatory")
        @DecimalMin(value = "1.00", message = "amountInr must be at least 1.00")
        BigDecimal amountInr
) {}
