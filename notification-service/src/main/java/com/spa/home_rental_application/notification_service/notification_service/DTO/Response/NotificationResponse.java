package com.spa.home_rental_application.notification_service.notification_service.DTO.Response;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String userId,
        NotificationType type,
        NotificationCategory category,
        String recipient,
        String subject,
        String message,
        NotificationStatus status,
        int retryCount,
        String errorMessage,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {}
