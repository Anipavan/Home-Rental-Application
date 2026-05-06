package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.config.NotificationProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically picks up FAILED notifications whose retry budget hasn't
 * been exhausted and re-dispatches them. Frequency comes from
 * {@code app.notification.retry-interval-minutes} (default 5 min).
 */
@Component
@Slf4j
public class RetryScheduler {

    private final NotificationLogRepository repo;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties props;

    public RetryScheduler(NotificationLogRepository repo,
                          NotificationDispatcher dispatcher,
                          NotificationProperties props) {
        this.repo = repo;
        this.dispatcher = dispatcher;
        this.props = props;
    }

    /** Fixed-delay so two runs never overlap. */
    @Scheduled(fixedDelayString = "#{ ${app.notification.retry-interval-minutes:5} * 60 * 1000 }")
    public void retryFailed() {
        List<NotificationLog> stuck = repo.findByStatusAndRetryCountLessThan(
                NotificationStatus.FAILED, props.getMaxRetries());
        if (stuck.isEmpty()) return;
        log.info("Retrying {} failed notification(s)", stuck.size());
        for (NotificationLog n : stuck) {
            dispatcher.dispatch(n);
        }
    }
}
