package com.spa.home_rental_application.payment_service.payment_service.enums;

/**
 * Lifecycle status of a rent payment.
 * <pre>
 * PENDING ──pay──▶ PROCESSING ──gateway success──▶ PAID
 *    │                  │
 *    │                  └──gateway failure──▶ FAILED
 *    │
 *    ├──due-date passes──▶ OVERDUE ──pay──▶ PAID
 *    │
 *    └──flat vacated / manual──▶ CANCELLED
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    PAID,
    OVERDUE,
    FAILED,
    CANCELLED,
    REFUNDED
}
