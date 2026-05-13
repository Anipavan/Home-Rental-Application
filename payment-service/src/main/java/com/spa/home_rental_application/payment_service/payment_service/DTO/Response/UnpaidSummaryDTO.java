package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outstanding-dues summary for a single flat, exposed at
 * {@code GET /payments/flat/{flatId}/unpaid}. Used by property-service
 * (via Feign) when validating a tenant's "schedule vacate" request —
 * the spec requires every PENDING / OVERDUE invoice to be cleared
 * before vacate.
 *
 * <p>{@link #totalOutstanding} is the sum of {@code totalAmount}
 * across all PENDING + OVERDUE rows (i.e. rent + late fee combined).
 * {@link #unpaidCount} is the row count. {@link #invoiceNumbers}
 * is the list of human-readable invoice ids, useful for surfacing
 * to the tenant in the error message ("Invoice INV-… is overdue").
 */
public record UnpaidSummaryDTO(
        String flatId,
        int unpaidCount,
        BigDecimal totalOutstanding,
        List<String> invoiceNumbers
) {
    public static UnpaidSummaryDTO empty(String flatId) {
        return new UnpaidSummaryDTO(flatId, 0, BigDecimal.ZERO, List.of());
    }
}
