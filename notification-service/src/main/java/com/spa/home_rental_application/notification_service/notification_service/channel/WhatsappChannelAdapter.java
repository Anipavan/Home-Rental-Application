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
 * WhatsApp delivery via Twilio's Business API.
 *
 * <p>Twilio routes WhatsApp through the same {@code Message.creator}
 * call as SMS — the channel is selected by the {@code whatsapp:} prefix
 * on the from/to numbers. So one Twilio account + one credential pair
 * powers both adapters.
 *
 * <p>Sandbox accounts use the shared {@code whatsapp:+14155238886}
 * number; production accounts use a pre-registered sender. Set
 * {@code app.twilio.whatsapp-from-number} accordingly — the adapter
 * prepends the {@code whatsapp:} scheme itself so the property stays
 * a plain E.164 string.
 *
 * <p>Falls back to a stub log line when credentials are placeholders,
 * same as {@link SmsChannelAdapter}, so dev / pre-prod environments
 * boot cleanly.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "app.notification",
        name = "delivery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WhatsappChannelAdapter implements NotificationChannelAdapter {

    private static final String WHATSAPP_SCHEME = "whatsapp:";

    private final TwilioProperties props;

    public WhatsappChannelAdapter(TwilioProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (props.credentialsConfigured()) {
            Twilio.init(props.getAccountSid(), props.getAuthToken());
            log.info("WhatsApp adapter initialised with Twilio account ending …{}",
                    tail(props.getAccountSid()));
        } else {
            log.warn("WhatsApp adapter running in STUB mode — set real "
                    + "app.twilio.* credentials to enable delivery");
        }
    }

    @Override
    public NotificationType type() { return NotificationType.WHATSAPP; }

    @Override
    public void send(NotificationLog n) {
        String recipient = n.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("WhatsApp recipient (phone) is missing");
        }
        // Same E.164 normalisation as SmsChannelAdapter — many profile
        // phone columns store bare 10-digit Indian numbers, which the
        // Twilio WhatsApp API also rejects without a country prefix.
        String e164 = props.toE164(recipient);
        if (!props.credentialsConfigured()) {
            log.info("[WHATSAPP-STUB] to={} body={}", e164,
                    truncate(n.getMessage(), 200));
            return;
        }
        String fromNumber = props.getWhatsappFromNumber();
        if (fromNumber == null || fromNumber.isBlank()) {
            // Fall back to the SMS from-number if the operator hasn't
            // set a dedicated whatsapp-from. Twilio sandbox + most
            // small deployments use the same number for both.
            fromNumber = props.getFromNumber();
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException(
                    "Neither app.twilio.whatsapp-from-number nor app.twilio.from-number is set");
        }

        Message msg = Message.creator(
                new PhoneNumber(whatsappPrefix(e164)),
                new PhoneNumber(whatsappPrefix(fromNumber)),
                n.getMessage()
        ).create();
        log.info("Sent WhatsApp sid={} to={} status={}",
                msg.getSid(), e164, msg.getStatus());
    }

    /**
     * Prepend the {@code whatsapp:} scheme if the caller stored the
     * bare phone number. Idempotent — already-prefixed values pass
     * through unchanged, so manual admin edits don't double-prefix.
     */
    private static String whatsappPrefix(String number) {
        return number.startsWith(WHATSAPP_SCHEME) ? number : WHATSAPP_SCHEME + number;
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
