package com.spa.home_rental_application.payment_service.payment_service.enums;

/** Payment method chosen by the tenant when paying. */
public enum PaymentMethod {
    UPI,            // GPay / PhonePe / Paytm / BHIM / etc
    CARD,           // Credit / debit card
    NET_BANKING,    // Direct bank transfer via internet banking
    WALLET,         // Paytm / Amazon Pay / PhonePe wallet, etc
    BANK_TRANSFER,  // NEFT / RTGS / IMPS direct transfer (no gateway)
    CASH            // Recorded manually by owner
}
