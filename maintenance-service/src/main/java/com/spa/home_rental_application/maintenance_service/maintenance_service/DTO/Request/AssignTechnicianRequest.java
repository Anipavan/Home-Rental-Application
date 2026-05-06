package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

public record AssignTechnicianRequest(
        @NotBlank(message = "assignedTo is mandatory") String assignedTo
) {}
