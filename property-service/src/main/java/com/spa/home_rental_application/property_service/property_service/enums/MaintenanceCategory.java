package com.spa.home_rental_application.property_service.property_service.enums;

/**
 * Per-flat charge category, tagged by the maintainer when they
 * record the (flat, month) collection row. Surfaces in the
 * tenant-facing ledger so residents can see WHY their bill jumped
 * this month — without that, a notes-only field becomes the only
 * answer and the dashboard's UI loses any structured filterability.
 *
 * <p>The set is intentionally narrow + India-rental specific. Any
 * line item that doesn't match the first five lands in OTHER, which
 * the maintainer pairs with a free-text {@code notes} field for
 * context.
 *
 * <p>Stored as VARCHAR2(32) in {@code maintenance_collection.category}
 * via {@code @Enumerated(EnumType.STRING)} — adding a new value is a
 * one-line enum change, no migration. Removing or renaming an
 * existing value would need a data-migration to remap historic
 * rows; treat the set as additive-only.
 */
public enum MaintenanceCategory {
    /** Monthly water bill — meter-based or apportioned. */
    WATER_BILL,
    /** Generic per-flat maintenance dues (the building default). */
    MAINTENANCE,
    /** Cooking-gas usage if billed by the society (pipe-gas societies). */
    GAS_BILL,
    /** Per-flat electricity if not direct-metered by the utility. */
    ELECTRICITY,
    /** Common-area cost share (lift AMC, security salary, lobby cleaning). */
    COMMON_AREA_SHARE,
    /** Anything else — paired with a free-text notes line item. */
    OTHER
}
