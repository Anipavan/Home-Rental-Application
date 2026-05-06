package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;

/**
 * Strategy interface for the actual delivery mechanism (SMTP, Twilio, FCM,…).
 * Each {@link com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType}
 * has one impl. The dispatcher picks the right one at runtime.
 */
public interface NotificationChannelAdapter {

    /** Which channel type this adapter handles. */
    com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType type();

    /**
     * Deliver the notification synchronously. Throws on failure so the
     * caller can stamp the {@link NotificationLog} appropriately.
     */
    void send(NotificationLog log) throws Exception;
}
