package com.spa.home_rental_application.compliance_service.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for generating a GST invoice for a settled payment. Caller is
 * responsible for resolving tenantId / ownerId from the payment record.
 */
public record GenerateGstInvoiceRequest(
        @NotBlank String tenantId,
        @NotBlank String ownerId,
        @NotNull @DecimalMin("0.01") BigDecimal rentAmount,
        @NotNull BigDecimal annualRentEstimate,    // for GST applicability check
        LocalDate invoiceDate
) {
}
