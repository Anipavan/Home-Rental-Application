package com.spa.home_rental_application.user_service.user_service.DTO.Response;

/**
 * The subset of a user's bank-account details that's safe to share
 * with anyone who needs to PAY them — typically the tenant paying
 * rent to the flat's owner.
 *
 * <p>Critically this DOES NOT include the full account number — the
 * full number stays encrypted at rest and is never returned over
 * the wire to anyone except the row owner. The masked form is
 * enough for the payer to recognise the destination, and the
 * {@code upiId} is enough to actually pay via UPI / QR.
 *
 * <p>The endpoint that serves this shape
 * ({@code GET /users/bank-accounts/payout/{userId}}) is open to any
 * authenticated user — that's a deliberate widening from the
 * full-detail endpoint which is self-or-admin only. The trust call
 * is: "tenant of flat X needs to pay owner Y; sharing Y's UPI VPA
 * + bank name + IFSC + masked account is the platform's job".
 */
public record BankAccountPayoutDto(
        String accountHolderName,
        String bankName,
        /** Masked form — {@code XXXX XXXX 1234} — never the full number. */
        String accountNumberMasked,
        /** Full IFSC. Public information by definition (every bank
         *  branch's IFSC is on the bank's public website). */
        String ifscCode,
        String branch,
        String accountType,
        /** Optional UPI VPA (e.g. {@code user@oksbi}). When present the
         *  payer can scan a generated UPI QR to pay; when absent the
         *  payer is expected to use the bank-account details for
         *  NEFT/IMPS instead. */
        String upiId
) {}
