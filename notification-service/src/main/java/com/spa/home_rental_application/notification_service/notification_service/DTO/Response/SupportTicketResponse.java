package com.spa.home_rental_application.notification_service.notification_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportTicketResponse(
        String id,
        String userId,
        String userName,
        String userEmail,
        String userRole,
        String subject,
        String message,
        String contextUrl,
        String status,
        String adminResponse,
        String respondedBy,
        Instant respondedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
