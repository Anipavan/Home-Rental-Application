package com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String errorCode,
        String message,
        List<FieldErrorDto> fieldErrors,
        LocalDateTime timestamp
) {
    public ErrorResponse(String errorCode, String message, List<FieldErrorDto> fieldErrors) {
        this(errorCode, message, fieldErrors, LocalDateTime.now());
    }
}
