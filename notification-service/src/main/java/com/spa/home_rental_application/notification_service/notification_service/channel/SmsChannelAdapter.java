package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.TwilioProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Twilio-backed SMS delivery (stub).
 *
 * <p>The real implementation just adds the Twilio Java SDK and one call:
 * <pre>
 *   Twilio.init(props.getAccountSid(), props.getAuthToken());
 *   Message.creator(new PhoneNumber(n.getRecipient()),
 *                   new PhoneNumber(props.getFromNumber()),
 *                   n.getMessage()).create();
 * </pre>
 * Wired up identically to {@link EmailChannelAdapter}.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "true", matchIfMissing = true)
public class SmsChannelAdapter implements NotificationChannelAdapter {

    private final TwilioProperties props;

    public SmsChannelAdapter(TwilioProperties props) {
        this.props = props;
    }

    @Override
    public NotificationType type() { return NotificationType.SMS; }

    @Override
    public void send(NotificationLog n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new IllegalArgumentException("SMS recipient (phone) is missing");
        }
        // STUB: replace with Twilio SDK call
        log.info("[SMS-STUB] Would send via Twilio (sid={}, from={}) to={} message={}",
                props.getAccountSid(), props.getFromNumber(), n.getRecipient(),
                n.getMessage().substring(0, Math.min(80, n.getMessage().length())));
    }
}
