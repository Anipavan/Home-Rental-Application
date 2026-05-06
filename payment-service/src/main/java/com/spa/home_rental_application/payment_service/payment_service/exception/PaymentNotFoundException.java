package com.spa.home_rental_application.payment_service.payment_service.exception;

import lombok.Getter;

@Getter
public class PaymentNotFoundException extends RuntimeException {
    private final String errorCode;
    public PaymentNotFoundException(String message) {
        super(message);
        this.errorCode = "PAYMENT_NOT_FOUND";
    }
}
