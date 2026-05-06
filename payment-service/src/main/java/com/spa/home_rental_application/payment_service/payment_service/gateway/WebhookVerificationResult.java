package com.spa.home_rental_application.payment_service.payment_service.gateway;

/** Outcome of verifying a webhook payload sent by the gateway. */
public record WebhookVerificationResult(
        boolean valid,
        String  paymentId,
        String  transactionId,
        String  errorMessage
) {}
