package com.spa.home_rental_application.user_service.user_service.DTO.Response;

/**
 * Internal-only projection with the RAW account number — served by
 * {@code GET /users/bank-accounts/internal/{userId}}. Consumed by
 * payment-service when registering an owner with Cashfree Easy Split;
 * Cashfree needs the raw number to run its penny-drop verification.
 *
 * <p>Only three fields — deliberately minimal. Whatever the payment-
 * service caller doesn't strictly need for Cashfree registration stays
 * behind the masked endpoint. Reduces the blast radius if a Feign call
 * ever escapes intended trust boundaries.
 *
 * <p>Never expose this record via a public route. The path lives under
 * {@code /users/bank-accounts/internal/**} and is protected by the
 * shared {@code GatewayAuthFilter} — direct hits without a valid
 * gateway HMAC are rejected before this DTO is populated.
 */
public record BankAccountInternalDto(
        String accountNumber,     // RAW — not masked
        String ifscCode,
        String accountHolderName
) {}
