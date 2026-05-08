package com.spa.home_rental_application.kyc_service.Exceptionclass;

import lombok.Getter;

@Getter
public class KycAlreadyVerifiedException extends RuntimeException {
    private final String errorCode;

    public KycAlreadyVerifiedException(String message) {
        super(message);
        this.errorCode = "KYC_ALREADY_VERIFIED";
    }
}
