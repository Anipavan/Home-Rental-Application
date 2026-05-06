package com.spa.home_rental_application.payment_service.payment_service.enums;

/** Card network when method=CARD. Recorded for receipts/analytics only — never the PAN. */
public enum CardNetwork {
    VISA,
    MASTERCARD,
    RUPAY,
    AMEX,
    DINERS,
    DISCOVER,
    OTHER
}
