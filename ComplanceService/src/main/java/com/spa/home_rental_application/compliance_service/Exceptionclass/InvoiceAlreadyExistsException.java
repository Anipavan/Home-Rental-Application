package com.spa.home_rental_application.compliance_service.Exceptionclass;

import lombok.Getter;

@Getter
public class InvoiceAlreadyExistsException extends RuntimeException {
    private final String errorCode;

    public InvoiceAlreadyExistsException(String message) {
        super(message);
        this.errorCode = "INVOICE_ALREADY_EXISTS";
    }
}
