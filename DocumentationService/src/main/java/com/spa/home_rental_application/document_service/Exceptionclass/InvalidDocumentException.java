package com.spa.home_rental_application.document_service.Exceptionclass;

import lombok.Getter;

@Getter
public class InvalidDocumentException extends RuntimeException {
    private final String errorCode;

    public InvalidDocumentException(String message) {
        super(message);
        this.errorCode = "INVALID_DOCUMENT";
    }

    public InvalidDocumentException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
