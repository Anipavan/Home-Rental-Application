package com.spa.home_rental_application.kyc_service.Exceptionclass;

import lombok.Getter;

@Getter
public class KycProviderException extends RuntimeException {
    private final String errorCode;

    public KycProviderException(String message) {
        super(message);
        this.errorCode = "KYC_PROVIDER_ERROR";
    }

    public KycProviderException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "KYC_PROVIDER_ERROR";
    }
}
