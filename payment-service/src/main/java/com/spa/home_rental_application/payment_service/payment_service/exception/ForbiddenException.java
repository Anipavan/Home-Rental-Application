package com.spa.home_rental_application.payment_service.payment_service.exception;

/**
 * Thrown when the gateway-authenticated caller is missing the permission
 * to act on the requested payment. Handled in {@link GlobalExceptionHandler}
 * as a 403 with a user-friendly message — never leaks whether the row
 * actually exists.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
