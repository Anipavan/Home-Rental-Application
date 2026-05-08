package com.spa.home_rental_application.lease_service.Exceptionclass;

import lombok.Getter;

@Getter
public class LeaseDeedException extends RuntimeException {
    private final String errorCode;

    public LeaseDeedException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "LEASE_DEED_FAILED";
    }
}
