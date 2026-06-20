package com.spa.home_rental_application.auth_service.Dto.External;

/**
 * Response from payment-service's {@code POST
 * /payments/registration/create-pending}. The {@code paymentId} is the
 * PK of the new PENDING Payment row, embedded by auth-service in the
 * REG_PAY JWT it mints for the frontend.
 */
public record CreateRegistrationPaymentResponse(
        String paymentId
) {}
