package com.spa.home_rental_application.compliance_service.Exceptionclass;

import lombok.Getter;

@Getter
public class GstInvoiceNotFoundException extends RuntimeException {
    private final String errorCode;

    public GstInvoiceNotFoundException(String message) {
        super(message);
        this.errorCode = "GST_INVOICE_NOT_FOUND";
    }
}
