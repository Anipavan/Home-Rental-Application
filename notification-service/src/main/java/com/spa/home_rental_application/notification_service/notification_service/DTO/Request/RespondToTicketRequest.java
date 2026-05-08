package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondToTicketRequest(
        @NotBlank String respondedBy,
        @NotBlank @Size(max = 4000) String adminResponse,
        @NotBlank String newStatus      // IN_PROGRESS | RESOLVED | CLOSED
) {
}
