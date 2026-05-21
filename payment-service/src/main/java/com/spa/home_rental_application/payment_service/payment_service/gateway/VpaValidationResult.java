package com.spa.home_rental_application.payment_service.payment_service.gateway;

import lombok.Builder;

/**
 * Result of a server-side UPI VPA (Virtual Payment Address) validation call.
 *
 * <p>VPA validation answers two questions in one round-trip:
 * <ol>
 *   <li><b>Is this VPA reachable on the UPI rails?</b> i.e. does NPCI's
 *       central directory recognise the user@psp pair as currently
 *       active. Format-correct but unregistered handles ({@code foo@bar})
 *       return {@code valid=false} here.</li>
 *   <li><b>Whose VPA is it?</b> NPCI's directory returns the masked name
 *       on the bank account behind the VPA (e.g. "ANIRUDH P****"). We
 *       surface this verbatim so the user can sanity-check they're about
 *       to pay the person they think they're paying — the same affordance
 *       GPay / PhonePe / Paytm already show.</li>
 * </ol>
 *
 * <p>Returned shape is intentionally simple: a boolean + a name + an
 * optional failure reason. Gateway-specific cruft (Razorpay error codes,
 * NPCI sub-statuses) is collapsed into a single human-readable
 * {@code failureReason} so the frontend doesn't need to grow a switch
 * statement per gateway.
 *
 * @param valid          true iff the VPA exists on the UPI directory
 * @param vpa            the VPA we validated (echoed back so the caller can
 *                       trust the response identifies the right input)
 * @param customerName   masked bank-account holder name; null on failure
 * @param failureReason  human-readable description when {@code valid=false};
 *                       null on success
 * @param gatewayName    which gateway resolved this call (mock | razorpay) —
 *                       useful for log diffing
 */
@Builder
public record VpaValidationResult(
        boolean valid,
        String vpa,
        String customerName,
        String failureReason,
        String gatewayName
) {
}
