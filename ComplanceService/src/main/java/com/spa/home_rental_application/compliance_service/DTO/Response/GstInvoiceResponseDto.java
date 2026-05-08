package com.spa.home_rental_application.compliance_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GstInvoiceResponseDto(
        String id,
        String paymentId,
        String tenantId,
        String ownerId,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal rentAmount,
        Boolean gstApplicable,
        BigDecimal gstRatePercent,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        String pdfUrl,
        Boolean sentViaWhatsapp,
        LocalDateTime createdAt
) {
}
