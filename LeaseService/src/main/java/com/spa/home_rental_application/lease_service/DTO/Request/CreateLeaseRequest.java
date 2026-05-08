package com.spa.home_rental_application.lease_service.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLeaseRequest(
        @NotBlank String tenantId,
        @NotBlank String flatId,
        @NotBlank String ownerId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.01") BigDecimal rentAmount,
        @DecimalMin("0.00") BigDecimal securityDeposit,
        @DecimalMin("0.00") BigDecimal rentIncrementPercent,
        String state                       // for RERA stamping; optional
) {
}
