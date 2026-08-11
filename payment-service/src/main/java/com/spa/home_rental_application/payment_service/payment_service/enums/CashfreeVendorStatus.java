package com.spa.home_rental_application.payment_service.payment_service.enums;

/**
 * State machine for a Cashfree Easy Split vendor.
 *
 * <p>The prereqs (KYC verified + bank account saved) live in other
 * services; payment-service's Kafka listeners aggregate them and only
 * move the row past {@link #REGISTERING} once both fire.
 *
 * <p>Only {@link #ACTIVE} qualifies an owner for split-payment routing.
 * Anything else falls through to direct-UPI at payment-initiate time.
 */
public enum CashfreeVendorStatus {

    /** KYC verified but bank account not saved yet — or vice-versa. */
    PENDING_KYC,
    PENDING_BANK,

    /** Both prereqs met, POST /pg/easy-split/vendors in flight. */
    REGISTERING,

    /** Cashfree accepted the request; penny-drop bank verification pending. */
    IN_BANK_VALIDATION,

    /** Cashfree has activated the vendor — split-payments authorised. */
    ACTIVE,

    /** Cashfree rejected the registration (bad bank, KYC mismatch, etc.). */
    REJECTED,

    /** Our API call errored (network, 5xx). Retry-eligible. */
    FAILED;

    /** True when the vendor can appear in an {@code order_splits[]} array. */
    public boolean isPayoutReady() {
        return this == ACTIVE;
    }
}
