package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Save-search create / update payload. Every predicate field is
 * optional — a fully-empty search means "any vacant flat anywhere",
 * which is silly but technically valid (the UI guards against that).
 */
public record SavedSearchRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String city,
        Integer bedrooms,
        @PositiveOrZero BigDecimal minRent,
        @PositiveOrZero BigDecimal maxRent,
        @PositiveOrZero Double minAreaSqft,
        @Pattern(
                regexp = "^$|UNFURNISHED|SEMI_FURNISHED|FULLY_FURNISHED",
                message = "furnishingStatus must be UNFURNISHED, SEMI_FURNISHED or FULLY_FURNISHED"
        )
        String furnishingStatus,
        Boolean petFriendly,
        Boolean isActive
) {}
