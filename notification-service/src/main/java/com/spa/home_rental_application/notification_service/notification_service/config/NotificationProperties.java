package com.spa.home_rental_application.notification_service.notification_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification-side config. The {@code fromEmail} default is the fake
 * homerental.local domain — fine if you never actually send mail, but
 * real SMTP servers (Gmail) reject from-addresses they can't
 * authenticate as sender for. Override via
 * {@code app.notification.from-email} (or {@code APP_NOTIFICATION_FROM_EMAIL}
 * env var) so it matches your authenticated Gmail address.
 *
 * <p>application.yaml sets the default to {@code ${MAIL_USERNAME:no-reply@homerental.local}}
 * so when you supply {@code MAIL_USERNAME=foo@gmail.com} the from
 * address inherits automatically.
 */
@ConfigurationProperties(prefix = "app.notification")
@Getter
@Setter
public class NotificationProperties {
    private boolean deliveryEnabled = true;
    private String fromEmail = "no-reply@homerental.local";
    private String fromName  = "Hearth";
    private int maxRetries = 3;
    private int retryIntervalMinutes = 5;
}
