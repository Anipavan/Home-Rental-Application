package com.spa.home_rental_application.compliance_service.Exceptionclass;

import lombok.Getter;

@Getter
public class ReraNotFoundException extends RuntimeException {
    private final String errorCode;

    public ReraNotFoundException(String message) {
        super(message);
        this.errorCode = "RERA_NOT_FOUND";
    }
}
