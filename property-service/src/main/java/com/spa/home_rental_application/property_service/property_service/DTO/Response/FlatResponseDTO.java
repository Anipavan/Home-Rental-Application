package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response payload for a Flat. Embeds a small slice of the parent
 * Building (name + address + city) so single-flat views don't need a
 * second API call. The building* fields are nullable for the rare case
 * where the parent has been hard-deleted.
 *
 * <p>Filter-facing attributes ({@code furnishingStatus}, {@code petFriendly},
 * {@code availableFrom}, {@code depositAmount}, {@code description}) are
 * all nullable — legacy rows pre-migration will surface as null and the
 * filter UI treats null as "not specified" rather than excluding the row.
 */
public record FlatResponseDTO(
        String id,
        String buildingId,
        String buildingName,
        String buildingAddress,
        String buildingCity,
        String flatNumber,
        Integer floor,
        Integer bedrooms,
        Integer bathrooms,
        Double areaSqft,
        BigDecimal rentAmount,
        Boolean isOccupied,
        String tenantId,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        /* Listing attributes (NoBroker / 99acres filter parity) */
        String furnishingStatus,
        Boolean petFriendly,
        LocalDate availableFrom,
        BigDecimal depositAmount,
        String description,
        /* Issue #5 — tenant-initiated scheduled vacate. NULL when no
         * vacate is pending. When set, the flat is still occupied
         * (isOccupied=true) and the tenant continues to pay rent
         * until this date arrives; the daily VacateScheduler flips
         * the flat to vacant on this date. */
        LocalDate scheduledVacateDate,
        /* Free-text reason the tenant gave when scheduling vacate. Null
         * if no vacate scheduled, or scheduled from a legacy client that
         * didn't send a reason. Owner sees this on flat detail + the
         * 10-day warning email so they can act on recurring problems. */
        String scheduledVacateComments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /* Tenant-preference filters. Both default to TRUE on legacy
         * rows so older listings stay maximally inclusive — the
         * browse filter excludes a flat only when the owner has
         * explicitly turned the preference off. */
        Boolean acceptsBachelor,
        Boolean acceptsFamily
) {}
