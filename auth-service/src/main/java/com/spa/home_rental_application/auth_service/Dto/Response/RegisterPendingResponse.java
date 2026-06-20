package com.spa.home_rental_application.auth_service.Dto.Response;

import java.math.BigDecimal;

/**
 * Returned from {@code POST /auth/register/pending}. The auth row has
 * been persisted as {@code enabled=false,
 * disable_reason='REGISTRATION_PAYMENT_PENDING'} and a matching PENDING
 * Payment row exists on payment-service. The frontend uses the
 * returned bundle to launch the paywall:
 *
 * <ul>
 *   <li>{@code authUserId} — the new user's id, used only as a
 *       breadcrumb on subsequent calls.</li>
 *   <li>{@code paymentId} — the PENDING Payment row id; passed back
 *       on {@code /payments/registration/initiate} and {@code /verify}.</li>
 *   <li>{@code paymentToken} — short-lived JWT (purpose=REG_PAY,
 *       ttl 30 min) the frontend attaches as
 *       {@code Authorization: Bearer ...} on the two payment endpoints
 *       above. This is what lets a not-yet-logged-in user pay —
 *       payment-service's JWT filter accepts REG_PAY tokens only on
 *       those two paths and only for the matching paymentId.</li>
 *   <li>{@code amountInr} — the fee shown on the paywall card
 *       (currently &#8377;999, configurable via
 *       {@code app.maintainer-registration.fee-inr}).</li>
 * </ul>
 */
public record RegisterPendingResponse(
        Long authUserId,
        String paymentId,
        String paymentToken,
        BigDecimal amountInr
) {}
