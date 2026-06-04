package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import com.spa.home_rental_application.property_service.property_service.enums.CollectionStatus;
import com.spa.home_rental_application.property_service.property_service.enums.MaintenanceCategory;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-flat bill summary returned on the society ledger payload —
 * surfaced on the public read-only ledger so residents and visitors
 * can see at a glance which flats have settled the month's dues
 * and which haven't.
 *
 * <p>Intentionally minimal: flat number + per-category amounts +
 * status. NO tenant name, phone, or any other individually
 * identifying field. The public URL is open-on-link; we don't want
 * a stranger with the link to be able to map a flat number to a
 * person. Anyone who needs to know who lives in flat 002 is already
 * a resident and has that information.
 */
@Builder
public record PublicFlatBillResponse(
        String flatNumber,
        List<PublicChargeLineResponse> charges,
        BigDecimal totalDue,
        BigDecimal totalPaid,
        /** Overall status for the row — SETTLED if everything paid /
         *  waived, PARTIAL if some paid + some still due, PENDING if
         *  nothing paid yet, NONE if no charges recorded this month. */
        String overallStatus
) {

    /** One line item — same category + status enum the maintainer
     *  dashboard uses, scoped down to just the public-safe fields. */
    @Builder
    public record PublicChargeLineResponse(
            MaintenanceCategory category,
            BigDecimal amount,
            CollectionStatus status
    ) {}
}
