package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Owner setup request — called once per building from the
 * "Set up society" wizard. The owner must already exist for this
 * building (CallerSecurity check). The {@code maintainerUserId}
 * defaults to the calling owner if absent (self-assign).
 *
 * <p>{@code monthlyDueDay} is capped at 28 — short-month safety, no
 * confusing edge cases on Feb. {@code defaultPerFlatAmount} can be
 * 0 (some societies don't collect dues at all and only track
 * expenses), but never negative.
 */
public record SetupSocietyRequest(
        @NotNull
        @DecimalMin(value = "0.00", message = "Per-flat default cannot be negative")
        BigDecimal defaultPerFlatAmount,

        @Min(value = 1, message = "Day of month must be 1-28")
        @Max(value = 28, message = "Day of month must be 1-28")
        Integer monthlyDueDay,

        /** authUserId of the person who'll manage this society. Defaults
         *  to the calling owner (self-assign) if null/blank. */
        String maintainerUserId,

        @Size(max = 200)
        String societyDisplayName
) {
}
