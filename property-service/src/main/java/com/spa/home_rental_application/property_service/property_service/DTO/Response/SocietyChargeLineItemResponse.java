package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;

/**
 * One society-charge line item as it appears on the maintenance receipt
 * PDF. Returned by {@code GET /society/charges/by-payment/{paymentId}}
 * — payment-service fans these out into the receipt's line-item table
 * so a bulk Pay-all receipt itemises Water bill + Maintenance +
 * Common-area share separately instead of showing one lumped total.
 *
 * <p>Intentionally narrow — no tenant identifiers, no notes — just
 * what the receipt prints. Keeps the cross-service contract tight.
 */
public record SocietyChargeLineItemResponse(
        String category,
        String forMonth,
        BigDecimal amountDue
) {}
