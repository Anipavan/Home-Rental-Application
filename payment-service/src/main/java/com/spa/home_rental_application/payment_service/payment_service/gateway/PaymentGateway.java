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
}
