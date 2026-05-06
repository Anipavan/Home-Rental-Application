package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /payments/verify — confirms a gateway transaction completed. */
public record VerifyPaymentRequest(
        @NotBlank(message = "paymentId is mandatory")     String paymentId,
        @NotBlank(message = "gatewayOrderId is mandatory") String gatewayOrderId,
        @NotBlank(message = "transactionId is mandatory")  String transactionId,
        @NotBlank(message = "signature is mandatory")      String signature
) {}
