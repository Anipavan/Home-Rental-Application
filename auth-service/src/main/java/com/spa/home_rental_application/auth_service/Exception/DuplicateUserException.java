package com.spa.home_rental_application.auth_service.Exception;

import lombok.Getter;

@Getter
public class DuplicateUserException extends RuntimeException {
    private final String errorCode;
    public DuplicateUserException(String message) {
        super(message);
        this.errorCode = "DUPLICATE_USER";
    }
}
