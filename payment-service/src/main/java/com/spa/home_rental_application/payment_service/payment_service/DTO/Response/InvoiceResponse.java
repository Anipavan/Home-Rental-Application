package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import java.time.Instant;

public record InvoiceResponse(
        String id,
        String paymentId,
        String invoiceNumber,
        Instant generatedDate,
        String pdfUrl
) {}
