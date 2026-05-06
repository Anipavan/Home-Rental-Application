package com.spa.home_rental_application.notification_service.notification_service.DTO.Response;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;

import java.util.List;

public record TemplateResponse(
        String id,
        String name,
        NotificationCategory category,
        NotificationType type,
        String subject,
        String bodyTemplate,
        List<String> variables
) {}
