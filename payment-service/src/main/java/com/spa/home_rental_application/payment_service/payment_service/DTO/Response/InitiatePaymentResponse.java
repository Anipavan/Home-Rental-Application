package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;

import java.math.BigDecimal;

/**
 * Returned from POST /payments/initiate. The shape varies slightly by
 * method but every gateway returns at minimum a gateway-side order id.
 */
public record InitiatePaymentResponse(
        String paymentId,
        PaymentMethod paymentMethod,
        String gatewayName,
        String gatewayOrderId,
        BigDecimal amount,
        String currency,

        // For card / net-banking / wallet flows
        String redirectUrl,

        // For UPI intent flows on mobile (e.g. "upi://pay?pa=...&am=...&tn=...")
        String upiIntentUrl,

        // For UPI collect flows (gateway sends a request to the tenant's VPA)
        String upiCollectStatus,

        // For BANK_TRANSFER — instruct tenant to manually transfer to these
        String bankAccountNumber,
        String bankIfsc,
        String bankAccountName,

        /**
         * The active gateway's public key id (Razorpay's {@code key_id},
         * Stripe's publishable key, etc.). Surfaced so a frontend that
         * launches the gateway via a client-side modal (Razorpay
         * Checkout.js) can configure the modal without having the value
         * hard-coded in the bundle. Null on flows that don't need a
         * modal — the existing rent / society redirect flows ignore it.
         */
        String gatewayKeyId,

        /**
         * Cashfree's per-order {@code payment_session_id}. Populated only
         * on the Cashfree gateway path — the SPA passes it to Cashfree's
         * Checkout SDK to open their hosted payment UI. Null on other
         * gateways (Mock etc.); the frontend's Phase 6 pay page treats
         * null as "no Cashfree flow available, fall back to direct-UPI".
         */
        String paymentSessionId
) {}
