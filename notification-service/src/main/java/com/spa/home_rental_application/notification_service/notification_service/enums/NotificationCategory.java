package com.spa.home_rental_application.notification_service.notification_service.enums;

/**
 * Business category of the notification — used to look up which template
 * to render and whether the user has opted in for this category.
 */
public enum NotificationCategory {
    USER_REGISTRATION,
    PASSWORD_RESET,
    PAYMENT_CREATED,
    PAYMENT_REMINDER,
    PAYMENT_OVERDUE,
    PAYMENT_RECEIPT,
    MAINTENANCE_CREATED,
    MAINTENANCE_ASSIGNED,
    MAINTENANCE_RESOLVED,
    LEASE_WELCOME,
    LEASE_EXPIRY,
    GENERIC
}
