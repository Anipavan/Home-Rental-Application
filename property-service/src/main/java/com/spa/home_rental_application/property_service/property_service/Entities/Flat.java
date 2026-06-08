package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "flats")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Flat {

    @Id
    private String id;

    @Column(name = "building_id", nullable = false)
    private String buildingId;

    @Column(name = "flat_number", nullable = false)
    private String flatNumber;

    private Integer floor;

    private Integer bedrooms;

    private Integer bathrooms;

    @Column(name = "area_sqft")
    private Double areaSqft;

    @Column(name = "rent_amount")
    private BigDecimal rentAmount;

    @Column(name = "is_occupied", nullable = false)
    @Builder.Default
    private Boolean isOccupied = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * authUserId of whoever owns this specific flat (V8). Default for
     * legacy data is the building owner — see V8 migration backfill.
     * Once a flat is "sold" via the FLAT_OWNER membership-claim flow,
     * this swaps to the new owner's id.
     *
     * <p>Semantics:
     * <ul>
     *   <li>{@code flatOwnerId == building.ownerId} — the building
     *       owner still owns this unit. Rent flows to them, lease
     *       lists them as Party A. Same behaviour as pre-V8.</li>
     *   <li>{@code flatOwnerId != building.ownerId} — split
     *       ownership. Rent goes to flatOwnerId, lease lists THEM as
     *       Party A, society dues are billed to THEM.</li>
     *   <li>{@code flatOwnerId == tenantId} — owner-occupier. No
     *       landlord-tenant relationship; the flat-owner lives in
     *       their own flat. Dashboard hides lease + rent surfaces.</li>
     * </ul>
     */
    @Column(name = "flat_owner_id", length = 64)
    private String flatOwnerId;

    @Column(name = "lease_start_date")
    private LocalDate leaseStartDate;

    @Column(name = "lease_end_date")
    private LocalDate leaseEndDate;

    /**
     * Tenant-initiated scheduled vacate. NULL means no pending
     * vacate. When set, the flat is STILL occupied (isOccupied=true)
     * and the tenant continues to pay rent / use the app — only on
     * this effective date does the daily {@code VacateScheduler}
     * sweep flip {@code isOccupied=false} and clear {@code tenantId}.
     *
     * <p>Spec: tenant clicks "Schedule vacate" → today + 60 days
     * lands here. Owner is notified 10 days before this date
     * (idempotent via {@link #vacateWarningSentAt}).
     */
    @Column(name = "scheduled_vacate_date")
    private LocalDate scheduledVacateDate;

    /**
     * Free-text reason the tenant gave for vacating. Captured on the
     * Schedule-vacate dialog and surfaced to the owner in the
     * 10-day-prior warning notification + on the owner's flat detail
     * screen. Helps the owner plan re-letting and follow up on any
     * recurring property issues (poor maintenance, noisy neighbours,
     * affordability) — strictly informational, not a screening tool.
     *
     * <p>Optional — pre-existing scheduled vacates that pre-date this
     * column stay {@code null}. The frontend renders "Not provided"
     * for the null case.
     */
    @Column(name = "scheduled_vacate_comments", length = 1000)
    private String scheduledVacateComments;

    /**
     * Idempotency stamp for the owner's 10-day-prior vacate warning.
     * Set when {@code VacateScheduler} fires the
     * {@code TENANT_VACATING_NOTICE} notification so re-runs of the
     * daily cron don't spam the owner. Cleared if the tenant cancels
     * their scheduled vacate.
     */
    @Column(name = "vacate_warning_sent_at")
    private LocalDateTime vacateWarningSentAt;

    /* ─────────── Listing attributes (filter-facing) ───────────
     * These power the NoBroker / 99acres-style filters on the public
     * browse page. Nullable so legacy rows keep working — the filter
     * UI treats null as "not specified" and shows the listing
     * regardless of the filter setting. Owners fill them in via the
     * flat-create / flat-edit forms.
     */

    /** UNFURNISHED | SEMI_FURNISHED | FULLY_FURNISHED. Free-string for
     *  schema-evolution flexibility (lease history shows the value
     *  changes over time without an enum migration). */
    @Column(name = "furnishing_status", length = 32)
    private String furnishingStatus;

    /** Pet policy disclosed upfront — saves owners + tenants a
     *  back-and-forth that 99% of pet-owning renters need. */
    @Column(name = "pet_friendly")
    private Boolean petFriendly;

    /** Earliest date the flat is available to a new tenant. Powers
     *  the "moving in by X" filter. Independent of leaseStartDate —
     *  that's the CURRENT tenant's lease. */
    @Column(name = "available_from")
    private LocalDate availableFrom;

    /** Refundable security deposit. Most Indian landlords use a
     *  rent-multiple convention but the actual number is the only
     *  truth — capture it so the listing card and filters can use
     *  the real value instead of a "2× rent" guess. */
    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    /** Short marketing description shown on the listing card hover
     *  and the property-detail page. Owners can write up to 2000
     *  chars; keeps the schema flexible without going @Lob. */
    @Column(name = "description", length = 2000)
    private String description;

    /* ─────────── Tenant-preference filters ───────────
     * In Indian rentals, "bachelor not allowed" / "family only" is a
     * common restriction that significantly shrinks a renter's
     * shortlist. Surface these as explicit booleans rather than
     * leaving renters to read between the lines of free-text amenity
     * descriptions.
     *
     * Both default to TRUE so legacy listings (and owners who don't
     * care to filter) stay maximally inclusive — the filter only
     * excludes a flat when the owner has explicitly turned the
     * preference off.
     */

    /** True if the owner accepts bachelor (unmarried, often shared)
     *  tenants. Setting this to FALSE hides the listing from anyone
     *  who filters by "Bachelor friendly" on the browse page. */
    @Column(name = "accepts_bachelor")
    @Builder.Default
    private Boolean acceptsBachelor = true;

    /** True if the owner accepts family tenants (typically married
     *  couples with or without children). FALSE makes the listing
     *  invisible to the "Family friendly" filter. */
    @Column(name = "accepts_family")
    @Builder.Default
    private Boolean acceptsFamily = true;

    /**
     * V10: explicit "listed for rent" toggle. Default FALSE — newly-
     * created flats DON'T appear on the public browse until the
     * owner explicitly switches this on. Lets owner-occupied flats,
     * flats under renovation, sale-only flats, etc. stay invisible
     * without us having to infer intent from {@link #tenantId}.
     *
     * <p>Public browse query filters on this flag AND
     * {@code isOccupied == false}. The owner edits it from the
     * EditFlatDialog on /owner/flats.
     */
    @Column(name = "available_for_rent", nullable = false)
    @Builder.Default
    private Boolean availableForRent = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}