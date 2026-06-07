package com.spa.home_rental_application.property_service.property_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link NotificationClient}. We never
 * want a notification-service outage to break the property-service
 * scheduler — the matcher swallows the failure and the next run will
 * try again. Worst case the user gets the alert a few minutes late.
 */
@Component
@Slf4j
public class NotificationClientFallback implements NotificationClient {

    @Override
    public void sendEmail(SendBody body) {
        log.warn("notification-service unreachable — skipping saved-search alert userId={} subject={}",
                body == null ? null : body.userId(),
                body == null ? null : body.subject());
    }

    @Override
    public void notifyUser(NotifyUserBody body) {
        // Same swallow-and-log policy as sendEmail. Membership-claim
        // notifications are nice-to-have; the owner can still see the
        // pending claim on their dashboard.
        log.warn("notification-service unreachable — skipping notifyUser userId={} subject={}",
                body == null ? null : body.userId(),
                body == null ? null : body.subject());
    }
}
