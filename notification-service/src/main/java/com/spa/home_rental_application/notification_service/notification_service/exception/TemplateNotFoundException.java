package com.spa.home_rental_application.notification_service.notification_service.exception;

import lombok.Getter;

@Getter
public class TemplateNotFoundException extends RuntimeException {
    private final String errorCode;
    public TemplateNotFoundException(String message) {
        super(message);
        this.errorCode = "TEMPLATE_NOT_FOUND";
    }
}
