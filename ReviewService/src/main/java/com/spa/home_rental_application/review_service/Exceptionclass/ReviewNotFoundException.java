package com.spa.home_rental_application.review_service.Exceptionclass;

import lombok.Getter;

@Getter
public class ReviewNotFoundException extends RuntimeException {
    private final String errorCode;

    public ReviewNotFoundException(String message) {
        super(message);
        this.errorCode = "REVIEW_NOT_FOUND";
    }
}
