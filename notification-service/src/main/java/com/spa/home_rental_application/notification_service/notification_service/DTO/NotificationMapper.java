package com.spa.home_rental_application.notification_service.notification_service.DTO;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.NotificationResponse;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.PreferenceResponse;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.TemplateResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponse toResponse(NotificationLog l) {
        if (l == null) return null;
        return new NotificationResponse(
                l.getId(), l.getUserId(), l.getType(), l.getCategory(),
                l.getRecipient(), l.getSubject(), l.getMessage(),
                l.getStatus(), l.getRetryCount(), l.getErrorMessage(),
                l.getSentAt(), l.getCreatedAt(), l.getUpdatedAt()
        );
    }

    public static PreferenceResponse toResponse(UserNotificationPreference p) {
        if (p == null) return null;
        return new PreferenceResponse(
                p.getUserId(), p.getEmail(), p.getPhone(), p.getDeviceToken(),
                p.isEmailEnabled(), p.isSmsEnabled(), p.isWhatsappEnabled(),
                p.isPushEnabled(), p.getMutedCategories()
        );
    }

    public static TemplateResponse toResponse(NotificationTemplate t) {
        if (t == null) return null;
        return new TemplateResponse(
                t.getId(), t.getName(), t.getCategory(), t.getType(),
                t.getSubject(), t.getBodyTemplate(), t.getVariables()
        );
    }
}
