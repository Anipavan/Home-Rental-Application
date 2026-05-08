package com.spa.home_rental_application.compliance_service.Exceptionclass;

import lombok.Getter;

@Getter
public class ReraAlreadyRegisteredException extends RuntimeException {
    private final String errorCode;

    public ReraAlreadyRegisteredException(String message) {
        super(message);
        this.errorCode = "RERA_ALREADY_REGISTERED";
    }
}
