package com.spa.home_rental_application.payment_service.payment_service.gateway;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;

/**
 * Strategy interface that abstracts away which third-party payment
 * processor we are talking to (Razorpay, Stripe, PayU, Paytm, …).
 *
 * <p>Implementations are wired by {@code PaymentGatewayConfig} based on
 * {@code app.payment.gateway} (mock | razorpay | stripe). Adding a new
 * processor is just adding another impl + a new conditional bean.
 *
 * <p>Each impl handles all the payment methods the underlying gateway
 * supports (UPI / cards / net-banking / wallets); the {@link Payment}
 * already carries the chosen {@code paymentMethod} so the impl knows
 * which gateway sub-flow to invoke.
 */
public interface PaymentGateway {

    /** Human-readable gateway name — written into Payment.gatewayName. */
    String name();

    /**
     * Step 1: turn a Payment + InitiatePaymentRequest into something the
     * client can act on (redirect URL, UPI intent, etc.).
     */
    PaymentInitiationResult initiate(Payment payment, InitiatePaymentRequest req);

    /**
     * Step 2: when the client returns from the gateway, verify the
     * signature/transaction id is genuinely from the gateway and not
     * spoofed by the client.
     */
    PaymentVerificationResult verify(Payment payment, VerifyPaymentRequest req);

    /**
     * Step 3 (optional): verify a webhook payload sent directly by the
     * gateway. Default impl just returns false (override per gateway).
     */
    default WebhookVerificationResult verifyWebhook(String rawBody, String signatureHeader) {
        return new WebhookVerificationResult(false, null, null, "WEBHOOK_NOT_SUPPORTED");
    }

    /**
     * Optional: validate that a UPI VPA exists on the NPCI directory and
     * return the masked holder name registered against it.
     *
     * <p>Real gateways (Razorpay, Cashfree, etc.) expose this as a free
     * or near-free server-to-server lookup. The Mock gateway returns a
     * deterministic stub for any well-formed VPA so the rest of the
     * stack can be exercised without external calls.
     *
     * <p>Default impl returns an "unsupported" result rather than
     * throwing — so a gateway that hasn't implemented this yet doesn't
     * crash the controller; the frontend degrades to format-only
     * validation in that case.
     */
    default VpaValidationResult validateVpa(String vpa) {
        return VpaValidationResult.builder()
                .valid(false)
                .vpa(vpa)
                .failureReason("VPA validation not supported by this gateway")
                .gatewayName(name())
                .build();
    }
}
