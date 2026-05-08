package com.spa.home_rental_application.document_service.Exceptionclass;

import lombok.Getter;

@Getter
public class DocumentNotFoundException extends RuntimeException {
    private final String errorCode;

    public DocumentNotFoundException(String message) {
        super(message);
        this.errorCode = "DOCUMENT_NOT_FOUND";
    }
}
