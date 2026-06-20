package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.CreateRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiateRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.CreateRegistrationPaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InitiatePaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.RegistrationPaymentResultResponse;

/**
 * Paid maintainer-signup orchestration on the payment-service side.
 * Three calls, each on its own endpoint:
 *
 * <ol>
 *   <li>{@link #createPending} — internal, auth-service Feigns into it
 *       when the user fills the "I'm a maintainer" form. Mints a
 *       {@code sourceType=MAINTAINER_REGISTRATION} Payment in PENDING.</li>
 *   <li>{@link #initiate} — REG_PAY-token-gated, the frontend hits it
 *       when the user clicks "Pay" on the paywall. Reuses the existing
 *       Razorpay gateway adapter to create an order and return the
 *       checkout-launch bundle.</li>
 *   <li>{@link #verify} — REG_PAY-token-gated, the frontend hits it
 *       on the success callback from Razorpay. Verifies HMAC, marks
 *       the Payment PAID, and Feigns into auth-service to flip the
 *       disabled auth row back to enabled.</li>
 * </ol>
 *
 * <p>If the {@code activateRegistration} Feign call fails after the
 * Payment is PAID, {@link com.spa.home_rental_application.payment_service.payment_service.scheduler.RegistrationActivationReconciler}
 * sweeps every 5 min and retries until auth-service answers.
 */
public interface RegistrationPaymentService {

    CreateRegistrationPaymentResponse createPending(CreateRegistrationPaymentRequest req);

    InitiatePaymentResponse initiate(InitiateRegistrationPaymentRequest req);

    RegistrationPaymentResultResponse verify(VerifyRegistrationPaymentRequest req, Long authUserIdFromToken);
}
