package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /payments/{id}/pay-cash — owner manually records a cash
 * payment received from the tenant. Skips the payment-gateway flow.
 */
public record PayCashRequest(
        @NotBlank(message = "ownerId is mandatory") String ownerId,
        @Size(max = 100) String reference   // optional: receipt #, cheque #, etc.
) {}
