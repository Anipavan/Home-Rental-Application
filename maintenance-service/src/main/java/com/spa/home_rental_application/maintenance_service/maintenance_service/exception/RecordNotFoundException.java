package com.spa.home_rental_application.maintenance_service.maintenance_service.exception;

import lombok.Getter;

@Getter
public class RecordNotFoundException extends RuntimeException {
    private final String errorCode;
    public RecordNotFoundException(String message) {
        super(message);
        this.errorCode = "RECORD_NOT_FOUND";
    }
}
