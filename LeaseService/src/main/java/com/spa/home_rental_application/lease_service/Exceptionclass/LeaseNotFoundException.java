package com.spa.home_rental_application.lease_service.Exceptionclass;

import lombok.Getter;

@Getter
public class LeaseNotFoundException extends RuntimeException {
    private final String errorCode;

    public LeaseNotFoundException(String message) {
        super(message);
        this.errorCode = "LEASE_NOT_FOUND";
    }
}
