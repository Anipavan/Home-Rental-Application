package com.spa.home_rental_application.notification_service.notification_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Twilio credentials, shared by both SMS and WhatsApp channels (the
 * Twilio Message API handles both through one account; the channel
 * is selected by the {@code whatsapp:} prefix on the from/to numbers).
 *
 * <p>{@link #accountSid} and {@link #authToken} default to literal
 * "placeholder" strings (see HRA-notification-service.yml) so the
 * service starts cleanly without real keys. The adapter classes
 * detect those placeholders and fall back to a stub-log path.
 */
@ConfigurationProperties(prefix = "app.twilio")
@Getter
@Setter
public class TwilioProperties {
    private String accountSid;
    private String authToken;

    /** Plain E.164 number for SMS, e.g. {@code +15551234567}. */
    private String fromNumber;

    /**
     * Twilio-side WhatsApp sender. Production accounts use a
     * pre-registered number, sandbox accounts use the shared
     * {@code +14155238886} number. Either way we prepend
     * {@code whatsapp:} in the adapter, not here, so the same
     * config value works for both.
     */
    private String whatsappFromNumber;

    /**
     * True when both SID and token look real (non-blank + not the
     * "placeholder" literal). Adapters use this to decide between
     * a real Twilio API call vs. a stub log line — lets the service
     * boot cleanly in dev without producing 401 errors at runtime.
     */
    public boolean credentialsConfigured() {
        return notPlaceholder(accountSid) && notPlaceholder(authToken);
    }

    private static boolean notPlaceholder(String s) {
        return s != null && !s.isBlank() && !s.equalsIgnoreCase("placeholder");
    }
}
