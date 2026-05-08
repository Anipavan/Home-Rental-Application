package com.spa.home_rental_application.lease_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaseResponseDto(
        String id,
        String tenantId,
        String flatId,
        String ownerId,
        String leaseNumber,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal rentAmount,
        BigDecimal securityDeposit,
        BigDecimal rentIncrementPercent,
        String status,
        String reraAgreementNumber,
        String documentUrl,
        String digitalSignatureStatus,
        BigDecimal aiRenewalProbability,
        LocalDateTime expiryWarningSentAt,
        LocalDateTime terminatedAt,
        String terminationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
