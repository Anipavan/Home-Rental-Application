package com.spa.home_rental_application.user_service.user_service.Exceptionclass;

import lombok.Getter;

/**
 * Thrown when a user write would violate a uniqueness invariant
 * (e.g. duplicate email).
 */
@Getter
public class DuplicateUserException extends RuntimeException {
    private final String errorCode;

    public DuplicateUserException(String message) {
        super(message);
        this.errorCode = "DUPLICATE_USER";
    }
}
