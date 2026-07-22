package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /payments/{id}/tenant-report-paid} — tenant
 * self-attests that they've completed a direct-UPI payment. All
 * fields optional; the paymentId in the path is enough on its own.
 */
public record TenantReportPaidRequest(
        /** Free-form note the tenant can add (e.g. their UPI txn ref
         *  copied from their bank SMS). Not required. */
        @Size(max = 500, message = "Note must be under 500 characters")
        String note
) {}
