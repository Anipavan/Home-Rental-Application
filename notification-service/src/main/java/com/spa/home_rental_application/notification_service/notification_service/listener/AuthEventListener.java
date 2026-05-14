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
        //
        // Issue #7 — include a sign-in URL so the welcome message has
        // a tappable CTA on every channel. The template references
        // {{signInUrl}} directly; if the variable is unset, Mustache
        // renders an empty string and the template gracefully degrades.
        notifications.fanOut(e.getAuthUserId(),
                NotificationCategory.USER_REGISTRATION,
                Map.of("userName",  safe(e.getUserName()),
                        "email",    safe(e.getEmail()),
                        "phone",    safe(e.getPhone()),
                        "role",     safe(e.getRole()),
                        "signInUrl", signInUrl()));
    }

    /**
     * Compute the absolute URL the user should hit to sign in. Built
     * off the same {@code app.frontend.base-url} property used by the
     * password-reset link so a single env override (FRONTEND_URL)
     * configures both. The fragment {@code #/sign-in} works for
     * SPA-hash routers and falls through to {@code /sign-in} for
     * history-mode routers (the FE's router redirects both forms).
     */
    private String signInUrl() {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:5173"
                : frontendBaseUrl.replaceAll("/+$", "");
        return base + "/sign-in";
    }

    @KafkaListener(
            topics = "${app.kafka.auth-topic:auth-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-password-reset",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent"}
    )
    public void onPasswordReset(PasswordResetRequestedEvent e) {
        if (e == null || !"user.password.reset.requested".equals(e.getEventType())) return;
        // Defensive: skip events with no resolvable user. After the
        // forgot-password endpoint change (auth-service now 404s on
        // unknown emails), we shouldn't see these, but keep the guard
        // for safety against any future decoy-style emitter.
        if (e.getAuthUserId() == null || e.getAuthUserId().isBlank()
                || e.getResetToken() == null || e.getResetToken().isBlank()) {
            log.debug("Ignoring password-reset event with no resolvable user.");
            return;
        }
        log.info("Received {} for authUserId={}", e.getEventType(), e.getAuthUserId());

        // Always log the reset link AT INFO so a developer running
        // without SMTP configured (no spring.mail.host → no
        // JavaMailSender bean → EmailChannelAdapter doesn't register)
        // can still complete the flow by copying the link from the
        // service log. In prod, a real SMTP server is configured and
        // this log line just doubles as an audit trail.
        String resetLink = resetLinkBaseUrl + "?token=" + e.getResetToken();
        log.info(
                "\n" +
                "============================================================\n" +
                " PASSWORD RESET LINK — copy to the user's browser if SMTP\n" +
                " isn't wired up (the email-channel falls back to a no-op\n" +
                " adapter when spring.mail.host is unset).\n" +
                "   user  : {}  <{}>\n" +
                "   token : {}\n" +
                "   link  : {}\n" +
                "   valid : until {}\n" +
                "============================================================",
                safe(e.getUserName()), safe(e.getEmail()),
                safe(e.getResetToken()), resetLink, safe(e.getExpiresAt()));

        // Deliberate single-channel email. See class-level javadoc for why.
        notifications.sendFromTemplate(e.getAuthUserId(), NotificationType.EMAIL,
                NotificationCategory.PASSWORD_RESET,
                Map.of("userName",  safe(e.getUserName()),
                        "email",    safe(e.getEmail()),
                        "token",    safe(e.getResetToken()),
                        "resetLink", resetLink,
                        "expiresAt",safe(e.getExpiresAt())));
    }

    /**
     * Public base URL the user's browser will hit. Defaults to the
     * frontend dev origin; override via FRONTEND_URL env var (or
     * app.frontend.base-url in application.yaml) for staging / prod.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${app.frontend.base-url:http://localhost:5173}/reset-password")
    private String resetLinkBaseUrl;

    /**
     * Same base URL as {@link #resetLinkBaseUrl}, but without the
     * {@code /reset-password} suffix. Lets us derive {@code /sign-in}
     * (Issue #7 — sign-in link in welcome notifications) from the
     * same configuration knob so operators only configure ONE value.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
