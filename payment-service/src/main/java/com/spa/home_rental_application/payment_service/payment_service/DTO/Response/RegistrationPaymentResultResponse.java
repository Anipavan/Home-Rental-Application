package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

/**
 * Returned from {@code POST /payments/registration/verify}. Carries
 * just enough for the frontend to route the user — the next stop after
 * activation is the {@code /login} screen with an "account activated"
 * banner.
 */
public record RegistrationPaymentResultResponse(
        String paymentId,
        String status,
        boolean accountActivated
) {}
