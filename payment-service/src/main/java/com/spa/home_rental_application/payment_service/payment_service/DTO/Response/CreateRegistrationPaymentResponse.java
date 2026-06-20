package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

/**
 * Returned from {@code POST /payments/registration/create-pending}.
 * The {@code paymentId} is the new PENDING Payment row's PK; auth-service
 * embeds it in the REG_PAY JWT it ships back to the frontend.
 */
public record CreateRegistrationPaymentResponse(String paymentId) {}
