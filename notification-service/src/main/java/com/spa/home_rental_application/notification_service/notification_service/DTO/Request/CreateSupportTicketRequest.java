package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank String userId,
        String userName,
        String userEmail,
        String userRole,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 500) String contextUrl
) {
}
