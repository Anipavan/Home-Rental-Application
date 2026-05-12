package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.PreferenceRequest;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import com.spa.home_rental_application.notification_service.notification_service.service.PreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code auth-events}.
 * <ul>
 *   <li>{@code user.registered} → fan a welcome across every channel
 *       the user is reachable on (in-app bell + email; SMS / WhatsApp
 *       light up later when the user adds a phone via the
 *       preferences page).</li>
 *   <li>{@code user.password.reset.requested} → reset-link email.
 *       Deliberately email-only — sending password-reset links over
 *       SMS / WhatsApp expands the attack surface for SIM-swap +
 *       account-takeover; auth-grade traffic stays single-channel.</li>
 * </ul>
 */
@Component
@Slf4j
public class AuthEventListener {

    private final NotificationService notifications;
    private final PreferenceService preferences;

    public AuthEventListener(NotificationService notifications,
                             PreferenceService preferences) {
        this.notifications = notifications;
        this.preferences = preferences;
    }

    @KafkaListener(
            topics = "${app.kafka.auth-topic:auth-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-user-registered",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent"}
    )
    public void onUserRegistered(UserRegisteredEvent e) {
        if (e == null || !"user.registered".equals(e.getEventType())) return;
        log.info("Received {} for authUserId={}", e.getEventType(), e.getAuthUserId());

        // Seed (or update) the UserNotificationPreference row from the
        // registration event. UserRegisteredEvent now carries the
        // phone the user typed at /auth/register (audit fix +
        // feature request: registration confirmation must reach the
        // user's mobile via WhatsApp + SMS, not just email).
        //
        // WhatsApp is enabled-by-default when a phone is present —
        // earlier code defaulted it OFF "explicit opt-in", which
        // meant registration confirmations never reached WhatsApp.
        // The product requirement is the opposite: welcome the user
        // on every channel they're reachable on; they can opt out
        // from the preferences page later. SMS and EMAIL stay on.
        boolean hasPhone = e.getPhone() != null && !e.getPhone().isBlank();
        try {
            preferences.upsert(e.getAuthUserId(), new PreferenceRequest(
                    e.getEmail(),
                    e.getPhone(),                  // phone — now in the event
                    null,                          // deviceToken
                    Boolean.TRUE,                  // emailEnabled
                    Boolean.TRUE,                  // smsEnabled
                    hasPhone ? Boolean.TRUE
                             : Boolean.FALSE,      // whatsappEnabled — on iff we have a number
                    Boolean.TRUE,                  // pushEnabled
                    null                           // muted categories
            ));
        } catch (Exception ex) {
            log.warn("Couldn't seed preferences for new user {}: {}",
                    e.getAuthUserId(), ex.getMessage());
        }

        // Welcome: fan across INAPP + EMAIL + SMS + WhatsApp. fanOut
        // handles opt-out / no-recipient gracefully so we don't have
        // to branch on hasPhone here — channels with no recipient just
        // record a SKIPPED row.
        notifications.fanOut(e.getAuthUserId(),
                NotificationCategory.USER_REGISTRATION,
                Map.of("userName", safe(e.getUserName()),
                        "email",   safe(e.getEmail()),
                        "phone",   safe(e.getPhone()),
                        "role",    safe(e.getRole())));
    }

    @KafkaListener(
            topics = "${app.kafka.auth-topic:auth-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-password-reset",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent"}
    )
    public void onPasswordReset(PasswordResetRequestedEvent e) {
        if (e == null || !"user.password.reset.requested".equals(e.getEventType())) return;
        log.info("Received {} for authUserId={}", e.getEventType(), e.getAuthUserId());
        // Deliberate single-channel email. See class-level javadoc for why.
        notifications.sendFromTemplate(e.getAuthUserId(), NotificationType.EMAIL,
                NotificationCategory.PASSWORD_RESET,
                Map.of("userName",  safe(e.getUserName()),
                        "email",    safe(e.getEmail()),
                        "token",    safe(e.getResetToken()),
                        "expiresAt",safe(e.getExpiresAt())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
