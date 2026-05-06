package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(
        @NotNull(message = "newStatus is mandatory") Status newStatus,
        @NotBlank(message = "changedBy is mandatory") String changedBy
) {}
