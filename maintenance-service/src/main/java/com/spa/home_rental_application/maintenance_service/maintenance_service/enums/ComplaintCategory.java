package com.spa.home_rental_application.maintenance_service.maintenance_service.enums;

/**
 * Category taxonomy for COMPLAINT-kind tickets.
 *
 * <p>Kept separate from {@link Category} (which lists physical
 * maintenance categories — plumbing, electrical, etc.) because the
 * two value sets share no overlap and mixing them into one enum
 * would force every UI dropdown to filter for the right subset.
 *
 * <p>Lineup chosen from rental-industry standards (NoBroker / Stanza
 * Living / Magicbricks complaint forms):
 *
 * <ul>
 *   <li>{@code NOISE} — neighbour music, late-night parties, traffic.</li>
 *   <li>{@code NEIGHBOR_DISPUTE} — non-noise interpersonal issues with
 *       another tenant or unit.</li>
 *   <li>{@code SECURITY_CONCERN} — entry-gate failures, broken locks,
 *       suspicious activity in common areas, missing CCTV.</li>
 *   <li>{@code OWNER_BEHAVIOR} — unannounced visits, harassment,
 *       privacy violations. Auto-routed to admin instead of the
 *       owner being complained about.</li>
 *   <li>{@code BILLING_DISPUTE} — disagreements about rent /
 *       maintenance charges / utility splits / deposit deductions.</li>
 *   <li>{@code SAFETY_HAZARD} — fire, structural, gas-leak risks
 *       that need urgent escalation.</li>
 *   <li>{@code COMMON_AREA} — cleanliness, lift breakdowns, garbage
 *       not collected, etc.</li>
 *   <li>{@code LEASE_VIOLATION} — owner letting a third party in,
 *       changing locks unilaterally, denied facilities listed in
 *       the agreement.</li>
 *   <li>{@code OTHER} — escape hatch.</li>
 * </ul>
 */
public enum ComplaintCategory {
    NOISE,
    NEIGHBOR_DISPUTE,
    SECURITY_CONCERN,
    OWNER_BEHAVIOR,
    BILLING_DISPUTE,
    SAFETY_HAZARD,
    COMMON_AREA,
    LEASE_VIOLATION,
    OTHER
}
