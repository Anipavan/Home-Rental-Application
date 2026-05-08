package com.spa.home_rental_application.review_service.Exceptionclass;

import lombok.Getter;

@Getter
public class InvalidReviewException extends RuntimeException {
    private final String errorCode;

    public InvalidReviewException(String message) {
        super(message);
        this.errorCode = "INVALID_REVIEW";
    }

    public InvalidReviewException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
