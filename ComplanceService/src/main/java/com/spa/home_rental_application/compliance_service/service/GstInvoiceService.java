package com.spa.home_rental_application.compliance_service.service;

import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateGstInvoiceRequest;
import com.spa.home_rental_application.compliance_service.DTO.Response.GstInvoiceResponseDto;

import java.io.IOException;

public interface GstInvoiceService {

    GstInvoiceResponseDto generate(String paymentId, GenerateGstInvoiceRequest request);

    GstInvoiceResponseDto getById(String invoiceId);

    /**
     * Find the GST invoice that was generated for a given payment.
     * Returns {@code null} if no GST invoice exists for the payment yet —
     * the frontend uses that to decide whether to show a "Download GST
     * invoice" button or not.
     */
    GstInvoiceResponseDto findByPaymentId(String paymentId);

    /** Streams the generated invoice PDF to the caller. */
    byte[] getPdf(String invoiceId) throws IOException;
}
