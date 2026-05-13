package com.spa.home_rental_application.document_service.Entities;

/**
 * Owner approval status for a tenant-uploaded document (Issue #9).
 *
 * <ul>
 *   <li>{@link #PENDING} — uploaded, awaiting owner review. Default
 *       for every newly-created Document.</li>
 *   <li>{@link #APPROVED} — the owner reviewed and accepted the
 *       document (e.g. address proof matches the rental address,
 *       income proof matches what the tenant declared).</li>
 *   <li>{@link #REJECTED} — the owner rejected it; the
 *       {@code rejectionReason} field holds the free-text reason
 *       the tenant sees in their notification + documents tab.
 *       The tenant can re-upload to restart the cycle (creates a new
 *       Document row at PENDING).</li>
 * </ul>
 *
 * <p>This is separate from the verifiedAt / verifiedBy admin /
 * KYC-provider verification concept. A document can be AUTO_VERIFIED
 * by the KYC OCR pipeline AND still be PENDING owner approval —
 * both states coexist, and the owner UI focuses on this enum.
 */
public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
