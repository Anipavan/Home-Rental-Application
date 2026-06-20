package com.spa.home_rental_application.payment_service.payment_service.gateway;

import lombok.Builder;
import lombok.Value;

/** What the gateway returns after we ask it to start a payment. */
@Value
@Builder
public class PaymentInitiationResult {
    /** Gateway-side order id — opaque to us, echoed back to the client. */
    String gatewayOrderId;
    /** The gateway implementation's name() value, e.g. "razorpay". */
    String gatewayName;
    /** For card / net-banking / wallet flows. */
    String redirectUrl;
    /** For mobile UPI intent: e.g. "upi://pay?pa=acme@upi&am=8500&tn=Rent". */
    String upiIntentUrl;
    /** For UPI collect requests: status the gateway returned synchronously. */
    String upiCollectStatus;
    /** For BANK_TRANSFER: instructions for the tenant. */
    String bankAccountNumber;
    String bankIfsc;
    String bankAccountName;
    /**
     * Gateway-side public key id (Razorpay's {@code key_id},
     * Stripe's publishable key, etc.). Surfaced so a frontend that
     * launches the gateway via a client-side modal — Razorpay
     * Checkout.js, Stripe Elements — can configure itself without
     * hard-coding the value in the bundle. Null on flows that
     * don't need a modal (the rent / society redirect flows
     * ignore it).
     */
    String publicKeyId;
}
