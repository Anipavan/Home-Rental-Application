package com.spa.home_rental_application.document_service.Exceptionclass;

import lombok.Getter;

@Getter
public class StorageException extends RuntimeException {
    private final String errorCode;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "STORAGE_ERROR";
    }

    public StorageException(String message) {
        super(message);
        this.errorCode = "STORAGE_ERROR";
    }
}
