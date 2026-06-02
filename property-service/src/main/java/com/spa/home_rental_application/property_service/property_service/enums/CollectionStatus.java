package com.spa.home_rental_application.property_service.property_service.enums;

/**
 * Lifecycle state of a {@code maintenance_collection} row — the
 * per-flat per-month "this flat owes ₹X for May" record. Status
 * transitions are linear; rows never go back to {@code DUE} once
 * they've been marked PAID or WAIVED (rectification = create a new
 * row for the next month, don't rewrite history).
 */
public enum CollectionStatus {
    /** Default state. The month is "open" and the tenant hasn't
     *  paid yet. Tenant dashboard shows this as a pending due. */
    DUE,

    /** Tenant paid. Until payment-integration ships, the maintainer
     *  flips this manually (after reconciling against their bank
     *  statement). Once auto-reconciliation is wired, the webhook
     *  handler flips it. */
    PAID,

    /** Owner / maintainer chose to skip this flat for this month —
     *  flat is vacant, owner-occupied, special hardship case, etc.
     *  WAIVED rows contribute to the audit trail (so a tenant can
     *  see "Flat 003 was waived this month, here's why") without
     *  affecting the outstanding-dues total. */
    WAIVED,

    /** Past the {@code monthly_due_day} configured on the building
     *  with no payment recorded. Cosmetic — flips back to PAID on
     *  payment. Computed in service-layer reads today; persisted
     *  here for the future payment-overdue cron. */
    OVERDUE
}
