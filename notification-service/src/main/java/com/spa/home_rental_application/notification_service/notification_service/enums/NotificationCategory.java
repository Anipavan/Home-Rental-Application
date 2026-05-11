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
    // Complaints share the maintenance pipeline but render different copy.
    COMPLAINT_CREATED,
    COMPLAINT_ACKNOWLEDGED,
    COMPLAINT_RESOLVED,
    LEASE_WELCOME,
    LEASE_EXPIRY,
    // ----- India Compliance Layer -----
    KYC_VERIFIED,
    KYC_FAILED,
    KYC_PAN_VERIFIED,
    LEASE_SIGNED,
    LEASE_RENEWED,
    LEASE_TERMINATED,
    RERA_REGISTERED,
    GST_INVOICE_GENERATED,
    DOCUMENT_VERIFIED,
    DOCUMENT_EXTRACTED,
    GENERIC
}
