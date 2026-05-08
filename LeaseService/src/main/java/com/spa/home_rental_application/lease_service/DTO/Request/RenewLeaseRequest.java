package com.spa.home_rental_application.lease_service.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RenewLeaseRequest(
        @NotNull LocalDate newEndDate,
        @DecimalMin("0.01") BigDecimal newRent,    // null → keep current rent
        String notes
) {
}
