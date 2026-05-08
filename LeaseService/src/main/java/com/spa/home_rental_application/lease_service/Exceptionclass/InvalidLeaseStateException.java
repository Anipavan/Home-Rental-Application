package com.spa.home_rental_application.lease_service.Exceptionclass;

import lombok.Getter;

@Getter
public class InvalidLeaseStateException extends RuntimeException {
    private final String errorCode;

    public InvalidLeaseStateException(String message) {
        super(message);
        this.errorCode = "INVALID_LEASE_STATE";
    }

    public InvalidLeaseStateException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
