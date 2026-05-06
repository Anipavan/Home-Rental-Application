package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TemplateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull NotificationCategory category,
        @NotNull NotificationType type,
        @Size(max = 200) String subject,
        @NotBlank @Size(max = 8000) String bodyTemplate,
        List<String> variables
) {}
