package com.spa.home_rental_application.payment_service.payment_service.gateway;

import lombok.Builder;
import lombok.Value;

/** Outcome of verifying a callback from the payment gateway. */
@Value
@Builder
public class PaymentVerificationResult {
    boolean success;
    String  transactionId;
    String  failureReason;
    String  gatewayErrorCode;
}
