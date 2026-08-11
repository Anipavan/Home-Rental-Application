package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.TwilioProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
/*
 * Registered ONLY when BOTH conditions below evaluate true:
 *   - app.notification.delivery-enabled = true (default true)
 *   - app.notification.channels.sms.enabled = true (default false)
 *
 * With SMS off by default, Twilio's Message API is never initialised
 * (no accountSid → no Twilio.init call → no accidental spend). Flip
 * app.notification.channels.sms.enabled=true once DLT registration is
 * done and TWILIO_SID/TOKEN/FROM env vars are set.
 *
 * Uses @ConditionalOnExpression rather than two @ConditionalOnProperty
 * annotations because ConditionalOnProperty is not repeatable — the
 * SpEL expression is the canonical way to AND multiple flags.
 */
@Component
@Slf4j
@ConditionalOnExpression(
        "${app.notification.delivery-enabled:true} "
        + "and ${app.notification.channels.sms.enabled:false}"
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
        // Normalise to E.164 before sending. Many profile rows store
        // bare 10-digit Indian numbers ("8088617923") which Twilio
        // rejects with error 21211; toE164 prepends the configured
        // default country code (+91) when the prefix is missing.
        String e164 = props.toE164(n.getRecipient());
        if (!props.credentialsConfigured()) {
            // Dev / pre-prod: log the would-be payload so we still see
            // the message in the audit trail but don't 4xx on a bad key.
            // Show the normalised form in the stub log so operators
            // can confirm the format that would actually be sent.
            log.info("[SMS-STUB] to={} body={}", e164,
                    truncate(n.getMessage(), 160));
            return;
        }
        if (props.getFromNumber() == null || props.getFromNumber().isBlank()) {
            throw new IllegalStateException(
                    "app.twilio.from-number is not configured — cannot send SMS");
        }
        Message msg = Message.creator(
                new PhoneNumber(e164),
                new PhoneNumber(props.getFromNumber()),
                n.getMessage()
        ).create();
        log.info("Sent SMS sid={} to={} status={}",
                msg.getSid(), e164, msg.getStatus());
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
