package com.spa.home_rental_application.notification_service.notification_service.enums;

/**
 * Channel a notification is delivered through.
 *
 * <p>{@code INAPP} is the always-available channel that backs the
 * notification bell in the SPA. Unlike {@code EMAIL} / {@code SMS} /
 * {@code PUSH}, it doesn't need an external recipient address — the
 * NotificationLog row itself is the delivery. Listeners should fan out
 * an INAPP variant alongside any EMAIL/SMS for every cross-role event
 * so the bell stays accurate even when SMTP / Twilio aren't configured.
 */
public enum NotificationType { EMAIL, SMS, PUSH, INAPP }
