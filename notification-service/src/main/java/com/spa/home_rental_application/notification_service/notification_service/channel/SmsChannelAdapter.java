package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.TwilioProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Twilio-backed SMS delivery.
 *
 * <p>Real Twilio call when {@link TwilioProperties#credentialsConfigured()}
 * returns true; falls back to a stub log line when SID/token are still
 * the literal "placeholder" defaults so the service runs cleanly in dev
 * without a Twilio account. The stub path is intentional, not an error —
 * it lets every other notification (INAPP, EMAIL) keep working while
 * the SMS leg is unconfigured.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "app.notification",
        name = "delivery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SmsChannelAdapter implements NotificationChannelAdapter {

    private final TwilioProperties props;

    public SmsChannelAdapter(TwilioProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (props.credentialsConfigured()) {
            // Twilio.init is idempotent — safe to call multiple times
            // if both SMS and WhatsApp adapters initialise.
            Twilio.init(props.getAccountSid(), props.getAuthToken());
            log.info("SMS adapter initialised with Twilio account ending …{}",
                    tail(props.getAccountSid()));
        } else {
            log.warn("SMS adapter running in STUB mode — set real "
                    + "app.twilio.account-sid / app.twilio.auth-token to enable delivery");
        }
    }

    @Override
    public NotificationType type() { return NotificationType.SMS; }

    @Override
    public void send(NotificationLog n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new IllegalArgumentException("SMS recipient (phone) is missing");
        }
        if (!props.credentialsConfigured()) {
            // Dev / pre-prod: log the would-be payload so we still see
            // the message in the audit trail but don't 4xx on a bad key.
            log.info("[SMS-STUB] to={} body={}", n.getRecipient(),
                    truncate(n.getMessage(), 160));
            return;
        }
        if (props.getFromNumber() == null || props.getFromNumber().isBlank()) {
            throw new IllegalStateException(
                    "app.twilio.from-number is not configured — cannot send SMS");
        }
        Message msg = Message.creator(
                new PhoneNumber(n.getRecipient()),
                new PhoneNumber(props.getFromNumber()),
                n.getMessage()
        ).create();
        log.info("Sent SMS sid={} to={} status={}",
                msg.getSid(), n.getRecipient(), msg.getStatus());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String tail(String s) {
        if (s == null || s.length() < 4) return "????";
        return s.substring(s.length() - 4);
    }
}
