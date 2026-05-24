package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A tenant's saved property search. The predicate fields define WHEN a
 * newly-listed flat should fire an alert; if all set predicates are
 * satisfied by a vacant flat, the user gets a notification.
 *
 * <p>Predicates are all nullable — null means "no constraint on this
 * dimension". Saved searches are conjunctive (AND of all set
 * predicates), matching how the browse UI's filters compose.
 *
 * <p>The {@code lastMatchedAt} column is the watermark for the
 * matcher scheduler — we only ever consider flats whose
 * {@code createdAt} is past this timestamp on each run, so users
 * don't get an alert for a flat they were already shown.
 *
 * <p>Keyed on auth user id (JWT subject) like {@code FlatFavorite},
 * so it survives any user-service profile rebuild.
 */
@Entity
@Table(
        name = "saved_searches",
        indexes = {
                @Index(name = "idx_savedsearch_user", columnList = "user_id"),
                @Index(name = "idx_savedsearch_active", columnList = "is_active")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Display name — defaults to a derived summary when omitted. */
    @Column(name = "name", length = 200)
    private String name;

    /* ─────────────── Predicate fields ─────────────── */

    /** Case-insensitive city name match. Null = any city. */
    @Column(name = "city", length = 100)
    private String city;

    /** Exact BHK (bedrooms) match. Null = any. */
    @Column(name = "bedrooms")
    private Integer bedrooms;

    /** Maximum acceptable rent (inclusive). Null = no max. */
    @Column(name = "max_rent", precision = 12, scale = 2)
    private BigDecimal maxRent;

    /** Minimum acceptable rent (inclusive). Null = no min. */
    @Column(name = "min_rent", precision = 12, scale = 2)
    private BigDecimal minRent;

    /** Minimum carpet area in sqft. Null = no min. */
    @Column(name = "min_area_sqft")
    private Double minAreaSqft;

    /** UNFURNISHED | SEMI_FURNISHED | FULLY_FURNISHED. Null = any. */
    @Column(name = "furnishing_status", length = 32)
    private String furnishingStatus;

    /** When true and set, only pet-friendly flats match. Null = no constraint. */
    @Column(name = "pet_friendly")
    private Boolean petFriendly;

    /* ─────────────── Bookkeeping ─────────────── */

    /**
     * Soft disable — users can pause alerts without deleting the search.
     * The matcher scheduler skips inactive rows.
     */
    // Same ORA-02258 trap as PropertyImage.is_cover — drop nullable=false
    // to avoid duplicate NOT NULL in the generated DDL.
    @Column(name = "is_active",
            columnDefinition = "NUMBER(1) DEFAULT 1 NOT NULL")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Watermark for the matcher — only flats listed after this point
     * are considered on the next run. Defaults to creation time so
     * the very first run doesn't blast the user with every existing
     * vacant flat in the catalog.
     */
    @Column(name = "last_matched_at")
    private Instant lastMatchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.lastMatchedAt == null) this.lastMatchedAt = now;
    }
}
