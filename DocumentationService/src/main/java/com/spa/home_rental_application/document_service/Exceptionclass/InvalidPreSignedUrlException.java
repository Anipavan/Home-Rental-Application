package com.spa.home_rental_application.document_service.Exceptionclass;

import lombok.Getter;

@Getter
public class InvalidPreSignedUrlException extends RuntimeException {
    private final String errorCode;

    public InvalidPreSignedUrlException(String message) {
        super(message);
        this.errorCode = "INVALID_PRESIGNED_URL";
    }
}
