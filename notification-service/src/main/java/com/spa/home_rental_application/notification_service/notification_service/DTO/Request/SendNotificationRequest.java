package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

/**
 * Body for the manual send endpoints (POST /notifications/send/email, etc.).
 * Use {@link #templateVariables} when {@link #category} is set and the
 * service should render from a template; otherwise provide the literal
 * {@link #subject} and {@link #message}.
 */
public record SendNotificationRequest(
        @NotBlank(message = "userId is mandatory") String userId,
        @NotNull(message = "type is mandatory") NotificationType type,
        NotificationCategory category,

        @Size(max = 200) String subject,
        @Size(max = 4000) String message,

        @Size(max = 200) String recipient,   // override email/phone/token if known

        Map<String, Object> templateVariables
) {
    public Map<String, Object> templateVariablesOrEmpty() {
        return templateVariables == null ? new HashMap<>() : templateVariables;
    }
}
