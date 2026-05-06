package com.spa.home_rental_application.payment_service.payment_service.exception;

import lombok.Getter;

@Getter
public class PaymentGatewayException extends RuntimeException {
    private final String errorCode;
    public PaymentGatewayException(String message) {
        super(message);
        this.errorCode = "PAYMENT_GATEWAY_ERROR";
    }
    public PaymentGatewayException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
