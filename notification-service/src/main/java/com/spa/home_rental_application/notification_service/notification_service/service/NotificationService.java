package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.DTO.NotificationMapper;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.SendNotificationRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.NotificationResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.exception.NotificationNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single entry point used by {@link com.spa.home_rental_application.notification_service.notification_service.controller.NotificationController}
 * (manual sends) and every Kafka listener. Handles preference checks,
 * template rendering, recipient lookup, and dispatch.
 */
@Service
@Slf4j
public class NotificationService {

    private final TemplateService templateService;
    private final PreferenceService preferenceService;
    private final NotificationDispatcher dispatcher;
    private final NotificationLogRepository logRepo;
    private final NotificationStreamRegistry streamRegistry;

    public NotificationService(TemplateService templateService,
                               PreferenceService preferenceService,
                               NotificationDispatcher dispatcher,
                               NotificationLogRepository logRepo,
                               NotificationStreamRegistry streamRegistry) {
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.dispatcher = dispatcher;
        this.logRepo = logRepo;
        this.streamRegistry = streamRegistry;
    }

    /* ------------- Manual sends ------------- */

    public NotificationResponse send(SendNotificationRequest req) {
        return NotificationMapper.toResponse(deliver(
                req.userId(), req.type(), req.category(),
                req.subject(), req.message(),
                req.recipient(), req.templateVariablesOrEmpty()));
    }

    /* ------------- Listener-facing ------------- */

    /**
     * Render-and-send for an inbound Kafka event. Handles preference
     * lookup + opt-out checks + template lookup transparently.
     */
    public NotificationLog sendFromTemplate(String userId,
                                            NotificationType type,
                                            NotificationCategory category,
                                            Map<String, Object> vars) {
        return deliver(userId, type, category, null, null, null, vars);
    }

    /**
     * Fan a notification out to a user via the in-app channel AND any
     * other channels that have a configured recipient. Listeners
     * should prefer this over single-channel
     * {@link #sendFromTemplate} so cross-role events always surface in
     * the bell even when SMTP / Twilio isn't wired up in dev.
     *
     * <p>{@code userId} blank → no-op (lets call sites pass a possibly-
     * null id from an event without a guard).
     */
    public void fanOut(String userId, NotificationCategory category, Map<String, Object> vars) {
        if (userId == null || userId.isBlank()) return;
        // INAPP first — backs the bell, always available.
        deliver(userId, NotificationType.INAPP, category, null, null, null, vars);
        // EMAIL is best-effort: if no email on the preference row it'll
        // record FAILED with "No recipient configured" and the bell
        // still shows the INAPP entry above.
        deliver(userId, NotificationType.EMAIL, category, null, null, null, vars);
    }

    /**
     * In-app-only convenience for ad-hoc cross-role pings that don't
     * have a template (e.g. "owner replied to your enquiry"). Pass
     * literal subject + message — bypasses template rendering.
     */
    public void sendInapp(String userId, NotificationCategory category,
                          String subject, String message,
                          Map<String, Object> vars) {
        if (userId == null || userId.isBlank()) return;
        deliver(userId, NotificationType.INAPP, category, subject, message, null,
                vars == null ? java.util.Map.of() : vars);
    }

    /* ------------- Lookups ------------- */

    public Page<NotificationResponse> list(Pageable pageable) {
        return logRepo.findAll(pageable).map(NotificationMapper::toResponse);
    }

    public List<NotificationResponse> getByUserId(String userId) {
        return logRepo.findByUserId(userId).stream().map(NotificationMapper::toResponse).toList();
    }

    public NotificationResponse getById(String id) {
        return NotificationMapper.toResponse(logRepo.findById(id).orElseThrow(
                () -> new NotificationNotFoundException("Notification not found: " + id)));
    }

    /* ------------- Read tracking ------------- */

    /**
     * Flip a single notification to {@link NotificationStatus#READ}.
     * Idempotent — calling on an already-read row is a no-op. Skips
     * terminal states (FAILED / SKIPPED) since they shouldn't be in
     * the bell anyway.
     */
    public NotificationResponse markAsRead(String id) {
        NotificationLog n = logRepo.findById(id).orElseThrow(
                () -> new NotificationNotFoundException("Notification not found: " + id));
        if (n.getStatus() == NotificationStatus.READ) {
            return NotificationMapper.toResponse(n);
        }
        if (n.getStatus() == NotificationStatus.FAILED
                || n.getStatus() == NotificationStatus.SKIPPED) {
            // Operational rows aren't "read-able" — leave the status
            // alone so the audit log stays truthful.
            return NotificationMapper.toResponse(n);
        }
        n.setStatus(NotificationStatus.READ);
        return NotificationMapper.toResponse(logRepo.save(n));
    }

    /**
     * Bulk: mark every unread (PENDING / SENT) notification for the
     * given user as READ. Powers the "open the bell, badge clears"
     * UX in one round trip.
     */
    public int markAllAsRead(String userId) {
        List<NotificationLog> unread = logRepo.findByUserIdAndStatusIn(
                userId,
                List.of(NotificationStatus.PENDING, NotificationStatus.SENT));
        if (unread.isEmpty()) return 0;
        for (NotificationLog n : unread) {
            n.setStatus(NotificationStatus.READ);
        }
        logRepo.saveAll(unread);
        log.info("Marked {} notifications as read for userId={}", unread.size(), userId);
        return unread.size();
    }

    /* ------------- Internal ------------- */

    private NotificationLog deliver(String userId,
                                    NotificationType type,
                                    NotificationCategory category,
                                    String subjectOverride,
                                    String messageOverride,
                                    String recipientOverride,
                                    Map<String, Object> vars) {

        UserNotificationPreference pref = preferenceService.findOrDefault(userId);

        // Opt-out check: muted category or channel disabled → record SKIPPED, don't send.
        if (category != null && pref.getMutedCategories() != null && pref.getMutedCategories().contains(category)) {
            return persist(userId, type, category,
                    recipientFor(type, pref, recipientOverride),
                    subjectOverride, messageOverride,
                    NotificationStatus.SKIPPED, "User opted out of " + category, vars);
        }
        if (!channelEnabled(type, pref)) {
            return persist(userId, type, category,
                    recipientFor(type, pref, recipientOverride),
                    subjectOverride, messageOverride,
                    NotificationStatus.SKIPPED, "Channel " + type + " disabled by user", vars);
        }

        String subject = subjectOverride;
        String body    = messageOverride;
        if ((subject == null || body == null) && category != null) {
            // Render from template
            try {
                NotificationTemplate tmpl = templateService.findOrThrow(category, type);
                if (subject == null) subject = templateService.render(tmpl.getSubject(), vars);
                if (body == null)    body    = templateService.render(tmpl.getBodyTemplate(), vars);
            } catch (Exception ex) {
                log.warn("Template lookup failed for category={} type={}: {}", category, type, ex.getMessage());
                // Fall back to a generic message so the user still hears about it.
                if (subject == null) subject = "Home Rental notification";
                if (body == null)    body = "You have a new " + category + " notification.";
            }
        }
        if (subject == null) subject = "Home Rental notification";
        if (body == null)    body    = "You have a new notification.";

        String recipient = recipientFor(type, pref, recipientOverride);
        if (recipient == null || recipient.isBlank()) {
            // EMAIL / SMS / PUSH without a configured recipient → still
            // record the attempt as FAILED for the audit log, but ALSO
            // write a sibling INAPP entry so the bell lights up. This
            // is what gets us cross-role notifications in dev where
            // nobody has email/phone set in their preferences.
            NotificationLog failed = persist(userId, type, category, recipient, subject, body,
                    NotificationStatus.FAILED, "No recipient configured for channel=" + type, vars);
            ensureInappSibling(userId, type, category, subject, body, pref, vars);
            return failed;
        }

        NotificationLog seed = persist(userId, type, category, recipient, subject, body,
                NotificationStatus.PENDING, null, vars);
        NotificationLog sent = dispatcher.dispatch(seed);
        // INAPP sibling for the bell — fire-and-forget so even if the
        // primary channel delivery fails mid-send, the bell entry is
        // still there.
        ensureInappSibling(userId, type, category, subject, body, pref, vars);
        return sent;
    }

    /**
     * Writes an INAPP sibling for non-INAPP deliveries so the SPA's
     * notification bell sees every cross-role event regardless of
     * whether EMAIL / SMS / PUSH actually went out. No-op when the
     * primary type is already INAPP (avoid infinite recursion) or
     * when INAPP is muted on the recipient's preference.
     */
    private void ensureInappSibling(String userId, NotificationType primary,
                                    NotificationCategory category,
                                    String subject, String body,
                                    UserNotificationPreference pref,
                                    Map<String, Object> vars) {
        if (primary == NotificationType.INAPP) return;
        if (!pref.isInappEnabled()) return;
        if (category != null && pref.getMutedCategories() != null
                && pref.getMutedCategories().contains(category)) {
            return;
        }
        try {
            NotificationLog inapp = persist(userId, NotificationType.INAPP, category,
                    userId, subject, body, NotificationStatus.PENDING, null, vars);
            dispatcher.dispatch(inapp);
        } catch (Exception ex) {
            // Bell-sibling is best-effort — never let it propagate and
            // kill the primary notification path.
            log.warn("INAPP sibling write failed for userId={} category={}: {}",
                    userId, category, ex.getMessage());
        }
    }

    private NotificationLog persist(String userId, NotificationType type, NotificationCategory category,
                                    String recipient, String subject, String body,
                                    NotificationStatus status, String error,
                                    Map<String, Object> vars) {
        NotificationLog logRow = NotificationLog.builder()
                .userId(userId)
                .type(type)
                .category(category != null ? category : NotificationCategory.GENERIC)
                .recipient(recipient)
                .subject(subject)
                .message(body)
                .status(status)
                .errorMessage(error)
                .retryCount(0)
                .metadata(vars == null ? new HashMap<>() : new HashMap<>(vars))
                .build();
        NotificationLog saved = logRepo.save(logRow);
        // Real-time push. INAPP rows are the bell-facing ones; the
        // others are operational. Pushing only the INAPP rows keeps
        // the SSE stream user-facing and avoids the FAILED-channel
        // noise from showing up in the bell.
        if (type == NotificationType.INAPP) {
            try {
                streamRegistry.pushToUser(saved);
            } catch (Exception ex) {
                log.warn("SSE push failed for notificationId={} (proceeding): {}",
                        saved.getId(), ex.getMessage());
            }
        }
        return saved;
    }

    private boolean channelEnabled(NotificationType t, UserNotificationPreference pref) {
        return switch (t) {
            case EMAIL    -> pref.isEmailEnabled();
            case SMS      -> pref.isSmsEnabled();
            case WHATSAPP -> pref.isWhatsappEnabled();
            case PUSH     -> pref.isPushEnabled();
            // In-app respects its own toggle; default for new users is
            // true (see PreferenceService.findOrDefault). Mute by
            // setting inappEnabled=false through the preferences API.
            case INAPP    -> pref.isInappEnabled();
        };
    }

    private String recipientFor(NotificationType t, UserNotificationPreference pref, String override) {
        if (override != null && !override.isBlank()) return override;
        return switch (t) {
            case EMAIL    -> pref.getEmail();
            // SMS + WhatsApp both use the user's phone. The Twilio
            // WhatsApp adapter prepends "whatsapp:" itself; we store
            // the bare E.164 number on the preference row.
            case SMS, WHATSAPP -> pref.getPhone();
            case PUSH     -> pref.getDeviceToken();
            // In-app is its own delivery — the userId is the recipient.
            // We never need an external address; the SPA bell reads
            // the NotificationLog directly via /notifications/user/{userId}.
            case INAPP    -> pref.getUserId();
        };
    }
}
