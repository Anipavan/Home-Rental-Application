package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-app channel — the NotificationLog row itself IS the delivery. The
 * SPA's notification bell reads /notifications/user/{userId} and picks
 * up every {@link NotificationLog} keyed on the signed-in user.
 *
 * <p>No external dependency (SMTP / Twilio / FCM), so this adapter is
 * always available — unlike {@link EmailChannelAdapter} which is gated
 * on a {@link org.springframework.mail.javamail.JavaMailSender} bean
 * existing. Every cross-role event (owner ↔ tenant) should fan out an
 * INAPP variant alongside any EMAIL/SMS so the bell stays accurate
 * even when SMTP isn't configured in dev.
 */
@Component
@ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled",
        havingValue = "true", matchIfMissing = true)
@Slf4j
public class InappChannelAdapter implements NotificationChannelAdapter {

    @Override
    public NotificationType type() {
        return NotificationType.INAPP;
    }

    @Override
    public void send(NotificationLog n) {
        // No-op transport — the persistence layer already wrote the row.
        // We just log so operators can trace fan-out without scraping
        // mongo. The dispatcher will then mark status=SENT/DELIVERED.
        log.info("[INAPP] userId={} category={} subject={}",
                n.getUserId(), n.getCategory(), n.getSubject());
    }
}
