package com.spa.home_rental_application.document_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponseDto(
        String id,
        String userId,
        String documentType,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        String ocrStatus,
        Map<String, String> extractedData,
        Boolean fraudFlag,
        BigDecimal confidenceScore,
        String verifiedBy,
        LocalDateTime verifiedAt,
        LocalDateTime uploadedAt,
        LocalDateTime updatedAt
) {
}
