package com.spa.home_rental_application.compliance_service.Exceptionclass;

import lombok.Getter;

@Getter
public class PdfGenerationException extends RuntimeException {
    private final String errorCode;

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PDF_GENERATION_FAILED";
    }
}
