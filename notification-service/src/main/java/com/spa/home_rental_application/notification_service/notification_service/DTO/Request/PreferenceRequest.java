package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record PreferenceRequest(
        @Email String email,
        @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "invalid phone")
        String phone,
        @Size(max = 500) String deviceToken,
        Boolean emailEnabled,
        Boolean smsEnabled,
        Boolean whatsappEnabled,
        Boolean pushEnabled,
        Set<NotificationCategory> mutedCategories
) {}
