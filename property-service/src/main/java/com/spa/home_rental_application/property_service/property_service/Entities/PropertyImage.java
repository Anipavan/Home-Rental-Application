package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "propertyimages")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PropertyImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "property_id", nullable = false)
    private String propertyId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "type")
    private String type; // e.g. "BUILDING", "FLAT", "THUMBNAIL", etc.

    /**
     * Cover photo flag. Exactly one image per propertyId should be
     * marked cover at any time — the service enforces that on
     * {@code setCover()}. The PropertyCard / property-detail hero
     * uses the cover image; the rest become the gallery thumbnails.
     *
     * <p>{@code columnDefinition} carries an explicit Oracle DEFAULT
     * so Hibernate's {@code ddl-auto=update} can ALTER an existing
     * populated {@code propertyimages} table to add this column.
     * Without the DEFAULT clause Oracle rejects the ALTER with
     * ORA-01758 (cannot add a NOT NULL column to a table that
     * already has data), which crashes property-service on startup
     * and 502s every wishlist / browse / image call downstream.
     * {@code @Builder.Default} is Java-side only — it has no effect
     * on DDL emission.
     */
    // NOTE: nullable=false intentionally omitted here. columnDefinition
    // already contains "NOT NULL"; with Hibernate's Oracle dialect
    // setting nullable=false ALSO appends " not null" to the column,
    // producing invalid DDL ("NUMBER(1) DEFAULT 0 NOT NULL not null")
    // → ORA-02258 on fresh table creation. Keep the NOT NULL inside
    // columnDefinition only.
    @Column(name = "is_cover",
            columnDefinition = "NUMBER(1) DEFAULT 0 NOT NULL")
    @Builder.Default
    private Boolean isCover = false;

    /**
     * Ascending sort order for the gallery view. Lower = earlier.
     * Owner-side editor exposes drag-reorder to mutate this. Defaults
     * to a large number on insert so newly-uploaded images land at
     * the end of the gallery until the owner reorders.
     *
     * <p>See {@link #isCover} for the columnDefinition rationale —
     * same Oracle ORA-01758 trap on populated tables.
     */
    // Same ORA-02258 trap as is_cover above — drop nullable=false to
    // avoid duplicate NOT NULL in the generated DDL.
    @Column(name = "sort_order",
            columnDefinition = "NUMBER(10) DEFAULT 1000 NOT NULL")
    @Builder.Default
    private Integer sortOrder = 1000;
}
