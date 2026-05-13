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
    // Visit + enquiry events from the public property-detail page.
    // VISIT_REQUESTED pings owner; VISIT_RESPONDED pings the visitor
    // (confirm / reschedule / cancel). ENQUIRY_RECEIVED is the
    // contact-owner flow.
    VISIT_REQUESTED,
    VISIT_RESPONDED,
    ENQUIRY_RECEIVED,
    LEASE_WELCOME,
    LEASE_EXPIRY,
    // Issue #5 — owner is notified 10 days before a tenant's
    // scheduled vacate date, so they can plan re-listing /
    // walkthrough / deposit return. Fanned across every channel
    // because vacates are high-impact ops events for the owner.
    TENANT_VACATING_NOTICE,
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
