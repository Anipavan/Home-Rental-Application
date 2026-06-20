package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /payments/registration/verify}. The frontend
 * forwards the three Razorpay-side params it received in the success
 * callback. The {@code paymentId} <em>must</em> match the one embedded
 * in the REG_PAY token claim; the controller refuses the call otherwise.
 */
public record VerifyRegistrationPaymentRequest(
        @NotBlank(message = "paymentId is mandatory")
        String paymentId,

        @NotBlank(message = "razorpayPaymentId is mandatory")
        String razorpayPaymentId,

        @NotBlank(message = "razorpayOrderId is mandatory")
        String razorpayOrderId,

        @NotBlank(message = "razorpaySignature is mandatory")
        String razorpaySignature
) {}
