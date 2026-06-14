package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Resident's "Pay all" body. Lists the collection rows the tenant wants
 * settled in one Razorpay order — typically every DUE / OVERDUE row in
 * the current month, but the FE may pass a subset (e.g. "Water bill +
 * Maintenance only, skip the disputed Common-area share").
 *
 * <p>Each collectionId is validated server-side: must exist, must belong
 * to a flat occupied by the caller, must currently be DUE or OVERDUE.
 * Rows already PAID or WAIVED are rejected with 422 — the FE should
 * never have offered them on the Pay-all screen.
 *
 * <p>The {@code Size(min=1, max=50)} cap stops a malicious caller from
 * shoving 10k ids in one request; for normal society sizes nobody has
 * more than ~10 charges in a month.
 */
public record InitiateSocietyChargePaymentRequest(
        @NotEmpty(message = "collectionIds must contain at least one row")
        @Size(min = 1, max = 50, message = "1-50 collections per Razorpay order")
        List<String> collectionIds
) {}
