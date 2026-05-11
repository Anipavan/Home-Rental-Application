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
     */
    @Column(name = "is_cover", nullable = false)
    @Builder.Default
    private Boolean isCover = false;

    /**
     * Ascending sort order for the gallery view. Lower = earlier.
     * Owner-side editor exposes drag-reorder to mutate this.
     * Defaults to a large number on insert so newly-uploaded images
     * land at the end of the gallery until the owner reorders.
     */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1000;
}
