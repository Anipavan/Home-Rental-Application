package com.spa.home_rental_application.auth_service.Dto.External;

import java.math.BigDecimal;

/**
 * Wire shape sent by auth-service to payment-service's
 * {@code POST /payments/registration/create-pending}.
 *
 * <p>{@code payerAuthUserId} is the freshly-created (disabled) auth
 * row's id — payment-service stores it on the Payment row so the
 * later /verify call can flip the right user's enabled flag back
 * on. {@code amountInr} is the fee resolved by auth-service from
 * the {@code app.maintainer-registration.fee-inr} property.
 */
public record CreateRegistrationPaymentRequest(
        String payerAuthUserId,
        BigDecimal amountInr
) {}
