package com.spa.home_rental_application.kyc_service.Exceptionclass;

import lombok.Getter;

@Getter
public class KycNotFoundException extends RuntimeException {
    private final String errorCode;

    public KycNotFoundException(String message) {
        super(message);
        this.errorCode = "KYC_NOT_FOUND";
    }
}
