package com.spa.home_rental_application.maintenance_service.maintenance_service.exception;

import lombok.Getter;

@Getter
public class IllegalStatusTransitionException extends RuntimeException {
    private final String errorCode;
    public IllegalStatusTransitionException(String message) {
        super(message);
        this.errorCode = "ILLEGAL_STATUS_TRANSITION";
    }
}
