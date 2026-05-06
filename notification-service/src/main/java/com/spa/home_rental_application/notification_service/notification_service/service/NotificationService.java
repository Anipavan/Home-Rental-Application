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

    public NotificationService(TemplateService templateService,
                               PreferenceService preferenceService,
                               NotificationDispatcher dispatcher,
                               NotificationLogRepository logRepo) {
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.dispatcher = dispatcher;
        this.logRepo = logRepo;
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
            return persist(userId, type, category, recipient, subject, body,
                    NotificationStatus.FAILED, "No recipient configured for channel=" + type, vars);
        }

        NotificationLog seed = persist(userId, type, category, recipient, subject, body,
                NotificationStatus.PENDING, null, vars);
        return dispatcher.dispatch(seed);
    }

    private NotificationLog persist(String userId, NotificationType type, NotificationCategory category,
                                    String recipient, String subject, String body,
                                    NotificationStatus status, String error,
                                    Map<String, Object> vars) {
        NotificationLog log = NotificationLog.builder()
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
        return logRepo.save(log);
    }

    private boolean channelEnabled(NotificationType t, UserNotificationPreference pref) {
        return switch (t) {
            case EMAIL -> pref.isEmailEnabled();
            case SMS   -> pref.isSmsEnabled();
            case PUSH  -> pref.isPushEnabled();
        };
    }

    private String recipientFor(NotificationType t, UserNotificationPreference pref, String override) {
        if (override != null && !override.isBlank()) return override;
        return switch (t) {
            case EMAIL -> pref.getEmail();
            case SMS   -> pref.getPhone();
            case PUSH  -> pref.getDeviceToken();
        };
    }
}
