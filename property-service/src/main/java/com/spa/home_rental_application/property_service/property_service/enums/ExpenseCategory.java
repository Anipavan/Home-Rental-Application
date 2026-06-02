package com.spa.home_rental_application.property_service.property_service.enums;

/**
 * High-level category for a {@code maintenance_expense} row. Drives
 * the colour-coded pills on the society ledger and the donut chart
 * breakdown ("how much of this month's spend went to utilities vs
 * staff salaries").
 *
 * <p>Intentionally coarse — the free-text {@code subcategory} field
 * on {@code MaintenanceExpense} carries the operator-friendly detail
 * (e.g. "BWSSB water bill — May", "Security guard Ramesh — May
 * salary"). We don't ship a 50-value subcategory enum because every
 * society has slightly different line items and we'd be shipping a
 * fresh deploy every time a treasurer wanted to track a new vendor.
 */
public enum ExpenseCategory {
    /** Water, common-area electricity, internet for common spaces, piped gas. */
    UTILITY,
    /** Security guard, gardener, cleaner, watchman, society manager. */
    SALARY,
    /** Cleaning supplies, light bulbs, paint, plumbing parts. */
    SUPPLIES,
    /** Lift AMC, generator service, intercom repair, pump repair. */
    REPAIR_COMMON,
    /** Society insurance, building insurance, common-area liability. */
    INSURANCE,
    /** Property tax, society registration fees, government dues. */
    TAX,
    /** Cultural events, Diwali decorations, holi colors, anything else. */
    OTHER
}
