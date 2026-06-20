package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /payments/registration/initiate}. The frontend
 * sends the paymentId (echoed back from the {@code RegisterPendingResponse}
 * bundle) + the user's chosen Razorpay method. Optional UPI fields
 * mirror the rent/society initiate request so a Razorpay UPI-collect
 * flow works the same here.
 */
public record InitiateRegistrationPaymentRequest(
        @NotBlank(message = "paymentId is mandatory")
        String paymentId,

        @NotNull(message = "paymentMethod is mandatory")
        PaymentMethod paymentMethod,

        String upiVpa
) {}
