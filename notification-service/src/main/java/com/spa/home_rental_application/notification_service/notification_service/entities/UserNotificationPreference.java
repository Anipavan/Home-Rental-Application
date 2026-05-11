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

    /**
     * WhatsApp uses the same phone-number recipient as SMS but routes
     * via Twilio's WhatsApp Business API. Off by default — many users
     * prefer SMS for transactional pings and WhatsApp for richer
     * messaging; opting in is an explicit choice on the preferences
     * page.
     */
    @Builder.Default
    @Field("whatsapp_enabled")
    private boolean whatsappEnabled = false;

    /**
     * In-app channel is always on by default. Powers the notification
     * bell. Users can mute it through the standard preferences flow if
     * they really want to.
     */
    @Builder.Default
    @Field("inapp_enabled")
    private boolean inappEnabled = true;

    /** Categories the user has explicitly opted OUT of. */
    @Builder.Default
    @Field("muted_categories")
    private Set<NotificationCategory> mutedCategories = new HashSet<>();
}
