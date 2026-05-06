package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.channel.NotificationChannelAdapter;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes a {@link NotificationLog} to the channel adapter that matches
 * its {@link NotificationLog#getType()}, persists the success/failure
 * outcome, and returns the updated entity.
 *
 * <p>Channel beans (Email/SMS/Push or their Noop counterparts) are
 * collected by Spring at startup and indexed by type, so adding a new
 * channel is just adding another {@link NotificationChannelAdapter} bean.
 */
@Component
@Slf4j
public class NotificationDispatcher {

    private final Map<NotificationType, NotificationChannelAdapter> adapters = new EnumMap<>(NotificationType.class);
    private final NotificationLogRepository logRepo;

    public NotificationDispatcher(List<NotificationChannelAdapter> channelBeans,
                                  NotificationLogRepository logRepo) {
        this.logRepo = logRepo;
        for (NotificationChannelAdapter a : channelBeans) {
            // Last writer wins — Conditional beans guarantee only one impl per type.
            adapters.put(a.type(), a);
        }
        log.info("NotificationDispatcher wired with {} adapter(s): {}",
                adapters.size(), adapters.keySet());
    }

    public NotificationLog dispatch(NotificationLog n) {
        NotificationChannelAdapter adapter = adapters.get(n.getType());
        if (adapter == null) {
            n.setStatus(NotificationStatus.FAILED);
            n.setErrorMessage("No adapter registered for type=" + n.getType());
            return logRepo.save(n);
        }
        try {
            adapter.send(n);
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(Instant.now());
            n.setErrorMessage(null);
        } catch (Exception ex) {
            log.warn("Notification {} failed: {}", n.getId(), ex.toString());
            n.setStatus(NotificationStatus.FAILED);
            n.setErrorMessage(ex.getMessage());
            n.setRetryCount(n.getRetryCount() + 1);
        }
        return logRepo.save(n);
    }
}
