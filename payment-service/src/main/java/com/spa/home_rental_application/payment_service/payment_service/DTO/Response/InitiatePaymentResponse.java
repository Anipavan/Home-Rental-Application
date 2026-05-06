package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;

import java.math.BigDecimal;

/**
 * Returned from POST /payments/initiate. The shape varies slightly by
 * method but every gateway returns at minimum a gateway-side order id.
 */
public record InitiatePaymentResponse(
        String paymentId,
        PaymentMethod paymentMethod,
        String gatewayName,
        String gatewayOrderId,
        BigDecimal amount,
        String currency,

        // For card / net-banking / wallet flows
        String redirectUrl,

        // For UPI intent flows on mobile (e.g. "upi://pay?pa=...&am=...&tn=...")
        String upiIntentUrl,

        // For UPI collect flows (gateway sends a request to the tenant's VPA)
        String upiCollectStatus,

        // For BANK_TRANSFER — instruct tenant to manually transfer to these
        String bankAccountNumber,
        String bankIfsc,
        String bankAccountName
) {}
