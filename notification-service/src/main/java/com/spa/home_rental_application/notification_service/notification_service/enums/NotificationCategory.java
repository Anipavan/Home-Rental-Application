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
    // Issue #9 — owner approval workflow on tenant-uploaded documents.
    // Fired when the owner approves/rejects from their tenant-detail
    // page. Recipient is the TENANT (the uploader) — they need to
    // know whether to wait, re-upload, or fix something specific
    // (rejectionReason variable on the REJECTED template).
    DOCUMENT_APPROVED,
    DOCUMENT_REJECTED,
    // Issue #9 — admin-composed announcements broadcast to a slice of
    // users (all, or filtered by role). No template — admin types raw
    // subject + body, then the broadcast endpoint fans the message out
    // to every recipient on INAPP + EMAIL.
    ADMIN_BROADCAST,
    GENERIC
}
