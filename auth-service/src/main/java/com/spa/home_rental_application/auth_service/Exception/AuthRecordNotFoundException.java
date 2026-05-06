package com.spa.home_rental_application.auth_service.Exception;

import lombok.Getter;

@Getter
public class AuthRecordNotFoundException extends RuntimeException {
    private final String errorCode;
    public AuthRecordNotFoundException(String message) {
        super(message);
        this.errorCode = "RECORD_NOT_FOUND";
    }
}
