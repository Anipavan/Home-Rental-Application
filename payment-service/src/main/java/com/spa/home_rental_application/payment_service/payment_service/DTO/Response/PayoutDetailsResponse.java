package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import java.math.BigDecimal;

/**
 * Returned by {@code GET /payments/{id}/payout-details} — everything
 * a tenant needs to pay an invoice directly to the owner's bank /
 * UPI without the platform sitting in the money path.
 *
 * <p>The {@code upiQrPayload} is a fully-formed UPI deep-link
 * (RFC 5870-style {@code upi://pay?...}). The frontend renders it
 * as a QR via {@code qrcode.react}; the tenant scans the QR in
 * GPay / PhonePe / Paytm and pays directly to the owner.
 *
 * <p>If the owner has saved a UPI VPA in their bank-account row,
 * {@code upiQrPayload} is non-null and the FE shows the QR.
 * Otherwise the FE falls back to displaying the bank-transfer
 * details (account number masked, IFSC, branch) for NEFT / IMPS.
 *
 * <p>Once the tenant has paid out-of-band, the OWNER comes back to
 * the platform and marks the invoice as "UPI received" or "Cash
 * received" — that's the call that flips the Payment row to PAID.
 * The platform never sees the actual money.
 */
public record PayoutDetailsResponse(
        String paymentId,
        BigDecimal amount,
        String invoiceReference,

        /* ── Payee identity ── */
        String payeeName,
        String ownerId,

        /* ── UPI (preferred path) ── */
        String upiVpa,
        /** Fully-formed UPI deep link, ready to QR-encode on the FE. */
        String upiQrPayload,

        /* ── Bank fallback (NEFT / IMPS) ── */
        String bankName,
        String accountNumberMasked,
        String ifscCode,
        String branch,
        String accountType,

        /** True when neither UPI nor bank details are on file — the
         *  FE renders an empty-state telling the tenant to contact
         *  the owner directly via the existing contact popover. */
        boolean ownerPayoutMissing
) {}
