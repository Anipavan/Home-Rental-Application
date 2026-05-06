package com.spa.home_rental_application.notification_service.notification_service.entities;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-user channel preferences. If no row exists for a user we fall back
 * to the all-channels-enabled default so they don't miss critical
 * messages (payment reminders, etc.).
 */
@Document(collection = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationPreference {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("user_id")
    private String userId;

    private String email;
    private String phone;
    @Field("device_token")
    private String deviceToken;

    @Builder.Default
    @Field("email_enabled")
    private boolean emailEnabled = true;

    @Builder.Default
    @Field("sms_enabled")
    private boolean smsEnabled = true;

    @Builder.Default
    @Field("push_enabled")
    private boolean pushEnabled = true;

    /** Categories the user has explicitly opted OUT of. */
    @Builder.Default
    @Field("muted_categories")
    private Set<NotificationCategory> mutedCategories = new HashSet<>();
}
