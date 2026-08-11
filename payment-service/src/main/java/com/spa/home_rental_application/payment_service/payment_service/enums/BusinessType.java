package com.spa.home_rental_application.payment_service.payment_service.enums;

/**
 * Business types accepted by Razorpay's Route onboarding API.
 * Each maps to a specific string Razorpay expects in the request
 * body (see {@link #toRazorpayValue}).
 *
 * <p>For our rental use case the overwhelming majority of owners are
 * {@link #INDIVIDUAL} (own the flat personally, no company) or
 * {@link #PROPRIETORSHIP} (small personal business). We list the
 * others because Razorpay accepts them and owners occasionally hold
 * property through a company.
 */
public enum BusinessType {
    INDIVIDUAL,
    PROPRIETORSHIP,
    PARTNERSHIP,
    PRIVATE_LIMITED,
    PUBLIC_LIMITED,
    LLP,
    TRUST,
    NGO,
    SOCIETY,
    HUF;   // Hindu Undivided Family — Razorpay accepts this too

    /** Emit the exact string Razorpay's API expects (lowercase snake). */
    public String toRazorpayValue() {
        return switch (this) {
            case INDIVIDUAL       -> "individual";
            case PROPRIETORSHIP   -> "proprietorship";
            case PARTNERSHIP      -> "partnership";
            case PRIVATE_LIMITED  -> "private_limited";
            case PUBLIC_LIMITED   -> "public_limited";
            case LLP              -> "llp";
            case TRUST            -> "trust";
            case NGO              -> "ngo";
            case SOCIETY          -> "society";
            case HUF              -> "huf";
        };
    }
}
