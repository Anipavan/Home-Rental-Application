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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}