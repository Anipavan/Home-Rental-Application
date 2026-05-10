package com.spa.home_rental_application.notification_service.notification_service.DTO.Response;

import java.time.Instant;

public record VisitRequestResponse(
        String id,
        String userId,
        String visitorName,
        String visitorEmail,
        String visitorPhone,
        String flatId,
        String buildingId,
        String ownerId,
        String propertyLabel,
        Instant preferredAt,
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
