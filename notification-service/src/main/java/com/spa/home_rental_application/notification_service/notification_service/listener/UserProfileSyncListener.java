package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.PreferenceRequest;
import com.spa.home_rental_application.notification_service.notification_service.service.PreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code user-events.user.profile.updated} and syncs the
 * tenant's email + phone into their {@link
 * com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference}
 * row. Without this, a user who registers with just an email and
 * later adds a phone via the profile page would never see SMS /
 * WhatsApp messages because the recipient address on the prefs row
 * stays null.
 *
 * <p>Idempotent — calling upsert with the same values is a no-op on
 * the DB layer. Channel-enabled flags are NOT touched here; the user
 * controls those via the preferences UI.
 */
@Component
@Slf4j
public class UserProfileSyncListener {

    private final PreferenceService preferences;

    public UserProfileSyncListener(PreferenceService preferences) {
        this.preferences = preferences;
    }

    @KafkaListener(
            topics = "${app.kafka.user-topic:user-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-user-profile-sync",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent"
            }
    )
    public void onProfileUpdated(UserProfileUpdatedEvent e) {
        if (e == null || !"user.profile.updated".equals(e.getEventType())) return;
        String authUserId = e.getAuthUserId();
        if (authUserId == null || authUserId.isBlank()) {
            log.debug("user.profile.updated has no authUserId — skipping prefs sync");
            return;
        }
        String email = blankToNull(e.getEmail());
        String phone = blankToNull(e.getPhone());
        if (email == null && phone == null) {
            // Nothing routable to sync.
            return;
        }
        try {
            preferences.upsert(authUserId, new PreferenceRequest(
                    email,
                    phone,
                    null,    // deviceToken — separate flow (push)
                    null,    // emailEnabled — don't toggle
                    null,    // smsEnabled — don't toggle
                    null,    // whatsappEnabled — don't toggle
                    null,    // pushEnabled — don't toggle
                    null     // mutedCategories — don't touch
            ));
            log.info("Synced profile contact info for authUserId={} (email={} phone={})",
                    authUserId, email != null, phone != null);
        } catch (Exception ex) {
            // Don't kill the listener loop over a sync hiccup.
            log.warn("Couldn't sync prefs for authUserId={}: {}",
                    authUserId, ex.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
