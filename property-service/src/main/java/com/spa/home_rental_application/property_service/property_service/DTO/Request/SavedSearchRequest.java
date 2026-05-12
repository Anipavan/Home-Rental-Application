package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Save-search create / update payload. Every predicate field is
 * optional — a fully-empty search means "any vacant flat anywhere",
 * which is silly but technically valid (the UI guards against that).
 *
 * <p>Audit M6: cross-field validation refuses {@code minRent &gt; maxRent}.
 * Previously a tenant could create a search with min=50000, max=10000
 * and the matcher silently never fired (every flat fails one bound).
 * The new validator returns 400 with a clear message instead of letting
 * the silent-zero-match state through.
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
) {
    /** Cross-field rent-range validator — M6. */
    @AssertTrue(message = "minRent must not exceed maxRent")
    public boolean isRentRangeValid() {
        if (minRent == null || maxRent == null) return true;
        return minRent.compareTo(maxRent) <= 0;
    }
}
