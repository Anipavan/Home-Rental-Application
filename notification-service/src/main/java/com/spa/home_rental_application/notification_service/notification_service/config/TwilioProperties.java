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
     * Default country dial-code to prepend when a recipient phone
     * number lacks the {@code +CC} prefix. Defaults to {@code +91}
     * (India) because the deployment is India-first; override via
     * {@code app.twilio.default-country-code} for multi-country
     * setups. Twilio rejects numbers without an E.164 country prefix
     * (error 21211), and many of our profile phone columns historically
     * store only the local 10-digit number — so we normalise at send-
     * time rather than asking every producer to canonicalise.
     */
    private String defaultCountryCode = "+91";

    /**
     * True when both SID and token look real (non-blank + not the
     * "placeholder" literal). Adapters use this to decide between
     * a real Twilio API call vs. a stub log line — lets the service
     * boot cleanly in dev without producing 401 errors at runtime.
     */
    public boolean credentialsConfigured() {
        return notPlaceholder(accountSid) && notPlaceholder(authToken);
    }

    /**
     * Convert a possibly-bare phone number to E.164 (e.g.
     * {@code 8088617923} → {@code +918088617923}). Behaviour:
     * <ul>
     *   <li>Already starts with {@code +} → returned unchanged (trim
     *       only). Caller is responsible for valid E.164.</li>
     *   <li>Starts with {@code 00} (international-dial prefix) → swap
     *       to {@code +}.</li>
     *   <li>Bare local number → prepend {@link #defaultCountryCode}.</li>
     *   <li>Null or blank → returned as-is (caller validates).</li>
     * </ul>
     * Non-digit characters (spaces, dashes, parens) are stripped from
     * the local-number path before prepending the country code so
     * "808 861 7923" and "(808) 861-7923" both end up as
     * {@code +918088617923}.
     */
    public String toE164(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();
        if (trimmed.startsWith("+")) return trimmed;
        if (trimmed.startsWith("00")) return "+" + trimmed.substring(2);
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) return raw;
        String cc = defaultCountryCode == null ? "+91" : defaultCountryCode.trim();
        if (!cc.startsWith("+")) cc = "+" + cc;
        return cc + digits;
    }

    private static boolean notPlaceholder(String s) {
        return s != null && !s.isBlank() && !s.equalsIgnoreCase("placeholder");
    }
}
