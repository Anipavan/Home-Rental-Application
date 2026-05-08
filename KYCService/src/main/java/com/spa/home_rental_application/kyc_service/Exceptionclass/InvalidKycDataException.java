package com.spa.home_rental_application.kyc_service.Exceptionclass;

import lombok.Getter;

@Getter
public class InvalidKycDataException extends RuntimeException {
    private final String errorCode;

    public InvalidKycDataException(String message) {
        super(message);
        this.errorCode = "INVALID_KYC_DATA";
    }

    public InvalidKycDataException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
