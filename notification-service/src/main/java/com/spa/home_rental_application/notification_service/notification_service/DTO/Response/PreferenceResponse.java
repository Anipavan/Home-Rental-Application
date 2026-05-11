package com.spa.home_rental_application.notification_service.notification_service.DTO.Response;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;

import java.util.Set;

public record PreferenceResponse(
        String userId,
        String email,
        String phone,
        String deviceToken,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean whatsappEnabled,
        boolean pushEnabled,
        Set<NotificationCategory> mutedCategories
) {}
