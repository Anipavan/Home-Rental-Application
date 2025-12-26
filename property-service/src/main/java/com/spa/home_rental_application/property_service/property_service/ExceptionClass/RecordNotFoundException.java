package com.spa.home_rental_application.property_service.property_service.ExceptionClass;


import lombok.Getter;

public class RecordNotFoundException extends RuntimeException {
    @Getter
    private final String errorCode;
    public RecordNotFoundException(String message){
        super(message);
        this.errorCode = "RECORD_NOT_FOUND";
    }
    public RecordNotFoundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
