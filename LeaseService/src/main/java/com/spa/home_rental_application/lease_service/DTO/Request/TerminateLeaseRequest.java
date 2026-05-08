package com.spa.home_rental_application.lease_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record TerminateLeaseRequest(
        @NotBlank String terminationReason,    // EARLY_TERMINATION | DEFAULT | MUTUAL | EXPIRY
        LocalDate terminationDate,
        String notes
) {
}
