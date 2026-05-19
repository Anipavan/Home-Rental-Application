package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Owner-side payload for creating/updating a flat.
 *
 * <p>The listing-attribute fields (furnishingStatus, petFriendly,
 * availableFrom, depositAmount, description) are all optional —
 * legacy clients that don't know about them still create flats fine,
 * and the FE filter UI shows those flats as "not specified" rather
 * than excluding them.
 */
public record FlatRequestDTO (
        @NotBlank(message = "Building ID is required")
        String buildingId,

        @NotBlank(message = "Flat number is required")
        String flatNumber,

        @NotNull(message = "Floor is required")
        @PositiveOrZero
        Integer floor,

        @NotNull(message = "Bedrooms is required")
        @Positive
        Integer bedrooms,

        @NotNull(message = "Bathrooms is required")
        @Positive
        Integer bathrooms,

        @NotNull(message = "Area is required")
        @Positive
        Double areaSqft,

        @NotNull(message = "Rent amount is required")
        @Positive
        BigDecimal rentAmount,

        String tenantId,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,

        /* ─────────── Listing attributes (filter-facing) ─────────── */

        /** UNFURNISHED | SEMI_FURNISHED | FULLY_FURNISHED. */
        @Pattern(
                regexp = "^$|UNFURNISHED|SEMI_FURNISHED|FULLY_FURNISHED",
                message = "furnishingStatus must be UNFURNISHED, SEMI_FURNISHED or FULLY_FURNISHED"
        )
        String furnishingStatus,

        Boolean petFriendly,

        LocalDate availableFrom,

        @PositiveOrZero
        BigDecimal depositAmount,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        /* ─────────── Tenant-preference filters ───────────
         * Both default to true server-side when the request omits
         * them — keeps legacy clients (no checkboxes in their flat
         * form) maximally inclusive. The browse-page filter only
         * excludes a flat when the owner has explicitly turned the
         * preference off.
         */

        /** True/null = bachelor tenants accepted. False = bachelors hidden
         *  from the listing when the filter is active. */
        Boolean acceptsBachelor,

        /** True/null = family tenants accepted. False = families hidden
         *  from the listing when the filter is active. */
        Boolean acceptsFamily
) {}
