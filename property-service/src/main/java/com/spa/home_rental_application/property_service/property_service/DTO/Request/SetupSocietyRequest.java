package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        String societyDisplayName,

        /* ─── Collection bank / UPI ───
         * All optional. Setting upi_id makes the tenant Pay-Now
         * button render a QR; null upi_id hides the button.
         *
         * <p>The @Pattern accepts empty (so the field can be cleared)
         * OR a well-formed VPA: 2-64 chars from [a-zA-Z0-9._-], an
         * '@', then a PSP handle of 2+ letters (oksbi, ybl, paytm,
         * axl, ibl, etc.). Format validation catches typos before the
         * QR is generated; actual VPA resolution requires a PSP call
         * we're not making at MVP. Payee-name-required-when-UPI-set
         * is enforced in {@code SocietyServiceImpl} (cross-field
         * checks don't fit @Pattern).
         */
        @Size(max = 64)
        @Pattern(regexp = "^$|^[a-zA-Z0-9._-]{2,64}@[a-zA-Z]{2,}$",
                message = "UPI ID must look like name@bank (e.g. anirudh@oksbi)")
        String upiId,

        @Size(max = 200)
        String payeeName,

        @Size(max = 32)
        String accountNumber,

        @Size(max = 16)
        @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "IFSC must be 4 letters, '0', then 6 alphanumerics")
        String ifscCode,

        /* ─── Cashfree Easy Split vendor KYC ───
         * PAN + phone + email + businessType are what Cashfree
         * requires to register the society as an Easy Split vendor.
         * Distinct from the maintainer user's personal KYC — a
         * society is legally its own payee. All optional (form can
         * be saved partially) but the vendor won't activate until
         * every field + bank details are set.
         */

        @Size(max = 10)
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$",
                message = "PAN must be 5 letters, 4 digits, 1 letter (e.g. ABCDE1234F)")
        String panNumber,

        @Size(max = 20)
        @Pattern(regexp = "^$|^(?:\\+?91)?[6-9][0-9]{9}$",
                message = "Phone must be a 10-digit Indian mobile (optional +91 prefix)")
        String contactPhone,

        @Size(max = 200)
        @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                message = "Contact email must be a valid address")
        String contactEmail,

        @Size(max = 64)
        String businessType
) {
}
