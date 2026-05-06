package com.spa.home_rental_application.payment_service.payment_service.exception;

import lombok.Getter;

@Getter
public class PaymentAlreadyPaidException extends RuntimeException {
    private final String errorCode;
    public PaymentAlreadyPaidException(String message) {
        super(message);
        this.errorCode = "PAYMENT_ALREADY_PAID";
    }
}
