package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.FcmProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging push notification adapter (stub).
 *
 * <p>The real implementation calls FCM HTTP v1 with the user's device
 * token (stored in their {@code UserNotificationPreference}).
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "true", matchIfMissing = true)
public class PushChannelAdapter implements NotificationChannelAdapter {

    private final FcmProperties props;

    public PushChannelAdapter(FcmProperties props) {
        this.props = props;
    }

    @Override
    public NotificationType type() { return NotificationType.PUSH; }

    @Override
    public void send(NotificationLog n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new IllegalArgumentException("Push recipient (device token) is missing");
        }
        // STUB: in production POST to https://fcm.googleapis.com/v1/projects/<id>/messages:send
        log.info("[PUSH-STUB] Would send via FCM (key=***{}) to={} title={} body={}",
                props.getServerKey() == null ? "" :
                        props.getServerKey().substring(Math.max(0, props.getServerKey().length() - 4)),
                n.getRecipient(), n.getSubject(), n.getMessage());
    }
}
