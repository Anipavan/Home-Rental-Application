package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import java.time.Instant;

public record ReceiptResponse(
        String id,
        String paymentId,
        String receiptNumber,
        Instant generatedDate,
        String pdfUrl
) {}
