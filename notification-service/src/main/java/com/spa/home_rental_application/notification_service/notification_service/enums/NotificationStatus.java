package com.spa.home_rental_application.notification_service.notification_service.enums;

/**
 * Lifecycle of a notification log row.
 *
 * <ul>
 *   <li>{@code PENDING} — persisted, waiting for the dispatcher.</li>
 *   <li>{@code SENT} — dispatcher handed it off to the channel adapter
 *       successfully. For INAPP this is the terminal "delivered" state
 *       from the bell's perspective until the user reads it.</li>
 *   <li>{@code FAILED} — channel adapter threw, or no recipient
 *       configured. Operational; bell filters these out.</li>
 *   <li>{@code RETRY} — failed but the retry scheduler will pick it up.</li>
 *   <li>{@code SKIPPED} — opt-out audit (channel disabled / category
 *       muted). Bell filters these out.</li>
 *   <li>{@code READ} — user opened the bell and saw the row. Used to
 *       decrement the unread badge.</li>
 * </ul>
 */
public enum NotificationStatus { PENDING, SENT, FAILED, RETRY, SKIPPED, READ }
