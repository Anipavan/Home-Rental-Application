package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /payments/{id}/revert-to-due} — owner (or
 * admin) flips a PAID payment back to DUE. Used when a tenant self-
 * reported via {@code /tenant-report-paid} but the money never
 * actually landed in the owner's bank, or when the owner marked a
 * payment PAID by mistake.
 *
 * <p>{@code reason} is optional but strongly recommended — the audit
 * event carries it so disputes have a paper trail.
 */
public record RevertPaymentRequest(
        @Size(max = 500, message = "Reason must be under 500 characters")
        String reason
) {}
