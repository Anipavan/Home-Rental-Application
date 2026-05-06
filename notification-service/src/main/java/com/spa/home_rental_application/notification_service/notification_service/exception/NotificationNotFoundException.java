package com.spa.home_rental_application.notification_service.notification_service.exception;

import lombok.Getter;

@Getter
public class NotificationNotFoundException extends RuntimeException {
    private final String errorCode;
    public NotificationNotFoundException(String message) {
        super(message);
        this.errorCode = "NOTIFICATION_NOT_FOUND";
    }
}
