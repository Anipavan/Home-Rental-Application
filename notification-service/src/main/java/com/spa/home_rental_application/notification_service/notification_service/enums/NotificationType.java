package com.spa.home_rental_application.notification_service.notification_service.enums;

/**
 * Channel a notification is delivered through.
 *
 * <p>{@code INAPP} is the always-available channel that backs the
 * notification bell in the SPA. Unlike {@code EMAIL} / {@code SMS} /
 * {@code WHATSAPP} / {@code PUSH}, it doesn't need an external recipient
 * address — the NotificationLog row itself is the delivery. Listeners
 * should fan out an INAPP variant alongside any EMAIL/SMS/WHATSAPP for
 * every cross-role event so the bell stays accurate even when external
 * providers aren't configured.
 *
 * <p>{@code WHATSAPP} reuses the Twilio Message API with a
 * {@code whatsapp:} prefix on the to/from numbers, so it shares the
 * same provider account as {@code SMS}.
 */
public enum NotificationType { EMAIL, SMS, WHATSAPP, PUSH, INAPP }
