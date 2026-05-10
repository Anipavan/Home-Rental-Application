package com.spa.home_rental_application.property_service.property_service.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated caller is asking to do something they
 * don't have rights to (e.g. an owner trying to vacate / assign a flat
 * in a building they don't own).
 *
 * <p>The {@code @ResponseStatus(FORBIDDEN)} annotation lets Spring
 * MVC's default exception resolver translate this into a 403 response
 * — the existing {@link com.spa.home_rental_application
 * .property_service.property_service.ExceptionHandler} catch-all will
 * also pick it up and surface a proper {@code APIErrorResponse}.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
