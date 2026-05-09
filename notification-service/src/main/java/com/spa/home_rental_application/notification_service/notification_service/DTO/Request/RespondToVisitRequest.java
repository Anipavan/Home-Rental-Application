package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /notifications/visit-requests/{id}/respond}.
 * Admin / owner sets a new status and optionally records a note that
 * gets emailed to the visitor.
 */
public record RespondToVisitRequest(
        @NotBlank
        @Pattern(regexp = "PENDING|CONFIRMED|COMPLETED|CANCELLED",
                 message = "newStatus must be one of PENDING|CONFIRMED|COMPLETED|CANCELLED")
        String newStatus,

        @Size(max = 2000) String adminResponse,

        @Size(max = 200) String respondedBy
) {
}
