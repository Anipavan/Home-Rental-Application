package com.spa.home_rental_application.auth_service.Exception;

import lombok.Getter;

@Getter
public class InvalidTokenException extends RuntimeException {
    private final String errorCode;
    public InvalidTokenException(String message) {
        super(message);
        this.errorCode = "INVALID_TOKEN";
    }
    public InvalidTokenException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
