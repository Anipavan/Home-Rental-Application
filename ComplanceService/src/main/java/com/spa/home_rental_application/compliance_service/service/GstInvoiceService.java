package com.spa.home_rental_application.compliance_service.service;

import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateGstInvoiceRequest;
import com.spa.home_rental_application.compliance_service.DTO.Response.GstInvoiceResponseDto;

import java.io.IOException;

public interface GstInvoiceService {

    GstInvoiceResponseDto generate(String paymentId, GenerateGstInvoiceRequest request);

    GstInvoiceResponseDto getById(String invoiceId);

    /** Streams the generated invoice PDF to the caller. */
    byte[] getPdf(String invoiceId) throws IOException;
}
