package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * When {@code app.notification.delivery-enabled=false} the real adapters
 * back off and these stand-in beans take over: every "send" is logged
 * and treated as a success. Used for local dev + integration tests so
 * we don't actually email anyone.
 */
public class NoopChannelAdapter {

    @Component("noopEmailAdapter")
    @ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "false")
    @Slf4j
    public static class Email implements NotificationChannelAdapter {
        @Override public NotificationType type() { return NotificationType.EMAIL; }
        @Override public void send(NotificationLog n) {
            log.info("[NOOP-EMAIL] to={} subject={}", n.getRecipient(), n.getSubject());
        }
    }

    @Component("noopSmsAdapter")
    @ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "false")
    @Slf4j
    public static class Sms implements NotificationChannelAdapter {
        @Override public NotificationType type() { return NotificationType.SMS; }
        @Override public void send(NotificationLog n) {
            log.info("[NOOP-SMS] to={} message={}", n.getRecipient(), n.getMessage());
        }
    }

    @Component("noopPushAdapter")
    @ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "false")
    @Slf4j
    public static class Push implements NotificationChannelAdapter {
        @Override public NotificationType type() { return NotificationType.PUSH; }
        @Override public void send(NotificationLog n) {
            log.info("[NOOP-PUSH] to={} title={} body={}", n.getRecipient(), n.getSubject(), n.getMessage());
        }
    }

    /**
     * INAPP doesn't actually need a noop — the real InappChannelAdapter
     * has no external dependency, so it's always available. The Noop
     * entry exists only when delivery-enabled=false to keep behaviour
     * symmetric with the other channels (no chance of a missing
     * dispatcher entry for INAPP).
     */
    @Component("noopInappAdapter")
    @ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "false")
    @Slf4j
    public static class Inapp implements NotificationChannelAdapter {
        @Override public NotificationType type() { return NotificationType.INAPP; }
        @Override public void send(NotificationLog n) {
            log.info("[NOOP-INAPP] userId={} subject={}", n.getUserId(), n.getSubject());
        }
    }

    /** Convenience for tests that just want all four at once. */
    public static List<Class<?>> allClasses() {
        return List.of(Email.class, Sms.class, Push.class, Inapp.class);
    }
}
