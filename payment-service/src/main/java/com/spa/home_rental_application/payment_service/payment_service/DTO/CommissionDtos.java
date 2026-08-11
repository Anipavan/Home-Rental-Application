package com.spa.home_rental_application.payment_service.payment_service.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spa.home_rental_application.payment_service.payment_service.entities.CommissionRule;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Container for the commission-rule request / response records. Kept in
 * one file so the small, tightly-coupled DTO family stays visible at a
 * glance — separating them into six single-record files would be more
 * import noise than signal.
 */
public final class CommissionDtos {

    private CommissionDtos() {}

    /**
     * Inbound payload for setting the global default rate OR upserting
     * a per-owner override. The admin form always sends the percentage
     * as a plain decimal (2 → 2.00 %, 0 → 0 %, 2.5 → 2.50 %); the
     * service converts to basis points before persisting so wire
     * math stays intuitive.
     */
    public record UpsertRuleRequest(
            @NotNull(message = "ratePercent is required")
            @DecimalMin(value = "0.00", message = "ratePercent must be ≥ 0")
            @DecimalMax(value = "100.00", message = "ratePercent must be ≤ 100")
            BigDecimal ratePercent,

            @Size(max = 500, message = "notes must be 500 characters or fewer")
            String notes
    ) {}

    /**
     * Outbound representation of a commission rule. Includes both the
     * basis-points form (what's persisted) and the percentage form
     * (what the admin sees in the UI) so the frontend doesn't have to
     * duplicate the conversion.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuleResponse(
            String     id,
            String     ownerId,
            Integer    rateBps,
            BigDecimal ratePercent,
            String     updatedByAdminId,
            String     notes,
            Instant    createdAt,
            Instant    updatedAt
    ) {
        public static RuleResponse from(CommissionRule r) {
            if (r == null) return null;
            BigDecimal pct = BigDecimal.valueOf(r.getRateBps())
                    .movePointLeft(2)
                    .stripTrailingZeros();
            return new RuleResponse(
                    r.getId(),
                    r.getOwnerId(),
                    r.getRateBps(),
                    pct.scale() < 0 ? pct.setScale(0) : pct,
                    r.getUpdatedByAdminId(),
                    r.getNotes(),
                    r.getCreatedAt(),
                    r.getUpdatedAt()
            );
        }
    }

    /**
     * Dry-run compute — powers the admin dashboard's live "on a ₹10,000
     * rent, ₹200 to you, ₹9,800 to the owner" preview strip.
     */
    public record PreviewResponse(
            BigDecimal totalAmount,
            BigDecimal platformFee,
            BigDecimal ownerAmount,
            Integer    appliedRateBps,
            BigDecimal appliedRatePercent,
            /** "global" or "owner" — tells the UI which rule fired. */
            String     source,
            /** Populated when source == "owner". */
            String     ownerId
    ) {}
}
