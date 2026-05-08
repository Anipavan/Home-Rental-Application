package com.spa.home_rental_application.document_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractedDataDto(
        String documentId,
        String documentType,
        Map<String, String> extractedData,
        Boolean fraudFlag,
        BigDecimal confidenceScore,
        String ocrStatus,
        LocalDateTime extractedAt
) {
}
