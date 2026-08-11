package com.spa.home_rental_application.payment_service.payment_service.enums;

/**
 * Lifecycle status of an owner's Razorpay Route Linked Account.
 *
 * <p>State transitions:
 * <pre>
 *   NOT_STARTED
 *        ↓  (owner submits KYC form)
 *   SUBMITTED
 *        ↓  (Razorpay begins review — sometimes instantly, sometimes minutes)
 *   UNDER_REVIEW
 *        ↓
 *   ACTIVATED ─────┐
 *        ↓         │
 *   SUSPENDED   REJECTED
 *        ↓
 *   ACTIVATED   NEEDS_CLARIFICATION
 * </pre>
 *
 * <p>Terminology maps to Razorpay's own account status codes:
 *   Razorpay {@code created} → SUBMITTED
 *   Razorpay {@code under_review} → UNDER_REVIEW
 *   Razorpay {@code activated} → ACTIVATED
 *   Razorpay {@code rejected} → REJECTED
 *   Razorpay {@code suspended} → SUSPENDED
 *   Razorpay {@code needs_clarification} → NEEDS_CLARIFICATION
 *
 * <p>Only ACTIVATED accounts can receive money via split payment. Any
 * other status → the payment order falls back to platform-only
 * routing (or, if the feature flag says so, direct-UPI path).
 */
public enum RouteAccountStatus {
    /** Owner hasn't started KYC yet. Local placeholder state — we never
     *  persist this to Razorpay; it exists only to differentiate
     *  "owner has never opened the form" from "owner submitted but
     *  Razorpay hasn't responded". */
    NOT_STARTED,

    /** We've successfully called Razorpay's {@code POST /v2/accounts}
     *  and got back a {@code razorpay_account_id}. Razorpay is
     *  processing. */
    SUBMITTED,

    /** Razorpay's compliance team is actively reviewing. Nothing to do
     *  from our side; wait for webhook. */
    UNDER_REVIEW,

    /** Approved. Ready to receive split payments. */
    ACTIVATED,

    /** Razorpay rejected the KYC. Owner needs to resubmit with
     *  corrected details. {@code rejection_reason} column carries the
     *  free-text explanation from Razorpay. */
    REJECTED,

    /** Was ACTIVATED, then Razorpay flagged something (suspicious
     *  activity, compliance re-review, owner-requested). Payments
     *  paused until resolved. */
    SUSPENDED,

    /** Razorpay reviewed the submission and needs additional documents
     *  from the owner. {@code rejection_reason} carries the specific
     *  ask ("please upload address proof of the primary stakeholder"
     *  etc.). Not a rejection — just paused. */
    NEEDS_CLARIFICATION;

    /** Convenience — true only when the account can actually receive
     *  a split payment from a Razorpay Route order. Every other status
     *  should fall back to non-Route payment routing. */
    public boolean isPayoutReady() {
        return this == ACTIVATED;
    }
}
