package com.spa.home_rental_application.user_service.user_service.Exceptionclass;

import lombok.Getter;

/**
 * Thrown when a user document upload specifies a document type that
 * is not in the allowed set (PROFILE, ID_PROOF).
 */
@Getter
public class InvalidDocumentTypeException extends RuntimeException {
    private final String errorCode;

    public InvalidDocumentTypeException(String message) {
        super(message);
        this.errorCode = "INVALID_DOCUMENT_TYPE";
    }
}
