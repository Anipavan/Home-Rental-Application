package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lease agreement DTO returned to the SPA. */
public record AgreementResponseDTO(
        String id,
        String flatId,
        String buildingId,
        String tenantId,
        String ownerId,
        BigDecimal rentAmount,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        String terms,
        String status,                    // PENDING_SIGNATURE | SIGNED | REJECTED
        String signatureData,             // base64 PNG, null until signed
        LocalDateTime signedAt,
        LocalDateTime rejectedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
