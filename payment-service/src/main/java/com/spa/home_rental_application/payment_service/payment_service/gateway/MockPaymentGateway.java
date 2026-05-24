package com.spa.home_rental_application.payment_service.payment_service.gateway;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import com.spa.home_rental_application.payment_service.payment_service.enums.UpiApp;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reference implementation that mimics a real gateway without ever
 * leaving the JVM. Used in dev/test and when no real gateway secret is
 * configured. Returns realistic-looking values so the rest of the system
 * (controllers, listeners, receipts) can be exercised end-to-end.
 *
 * <p>Method-specific outputs:
 * <ul>
 *   <li>UPI: returns a valid-looking {@code upi://} intent string</li>
 *   <li>CARD / NET_BANKING / WALLET: returns a "redirect" URL pointing at
 *       a stub success page on this service itself</li>
 *   <li>BANK_TRANSFER: returns dummy bank details</li>
 *   <li>CASH: shouldn't reach here (handled by /pay-cash), but if it does
 *       returns a no-op result</li>
 * </ul>
 *
 * <p>Verification accepts any {@code transactionId == "MOCK_OK"} as success;
 * any other value is treated as failure. Sufficient for integration tests.
 */
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    public static final String NAME = "mock";

    @Override
    public String name() { return NAME; }

    @Override
    public PaymentInitiationResult initiate(Payment payment, InitiatePaymentRequest req) {
        String orderId = "mock_" + UUID.randomUUID();
        var b = PaymentInitiationResult.builder()
                .gatewayName(NAME)
                .gatewayOrderId(orderId);

        PaymentMethod method = req.paymentMethod();
        switch (method) {
            case UPI -> {
                String vpa = req.upiVpa() != null ? req.upiVpa() : "rent@homerental";
                b.upiIntentUrl(buildUpiIntent(req.upiApp(), vpa,
                        payment.getTotalAmount().doubleValue(), orderId));
                b.upiCollectStatus("PENDING_USER_ACTION");
            }
            case CARD, NET_BANKING, WALLET -> {
                // Send the user to the in-house mock checkout page. The page
                // shows a fake card / netbanking form, then on "Pay" redirects
                // back to the tenant's returnUrl with verification params.
                //
                // Critical bug fix (was http://localhost:8084/...): hardcoded
                // host bound this to dev only. In prod the browser tried to
                // reach localhost:8084 → connection refused → CARD / NET_BANKING /
                // WALLET payments dead-ended. Use a relative path so the
                // SPA's same-origin axios baseURL ("/api/rentals/v1") resolves
                // it against whatever host is currently serving the SPA
                // (localhost in dev, anirudhhomes.in in prod).
                String urlEncodedReturn = req.returnUrl() == null
                        ? ""
                        : URLEncoder.encode(req.returnUrl(), StandardCharsets.UTF_8);
                b.redirectUrl("/api/rentals/v1/payments/mock/checkout"
                        + "?orderId=" + orderId
                        + "&paymentId=" + payment.getId()
                        + "&method=" + method.name()
                        + "&amount=" + payment.getTotalAmount().toPlainString()
                        + "&returnUrl=" + urlEncodedReturn);
            }
            case BANK_TRANSFER ->
                    b.bankAccountNumber("123456789012")
                     .bankIfsc("HRAH0000001")
                     .bankAccountName("Home Rental Escrow");
            case CASH -> {
                /* nothing — caller should use /pay-cash */
            }
        }
        log.info("MockPaymentGateway.initiate paymentId={} method={} orderId={}",
                payment.getId(), method, orderId);
        return b.build();
    }

    @Override
    public PaymentVerificationResult verify(Payment payment, VerifyPaymentRequest req) {
        boolean ok = "MOCK_OK".equals(req.transactionId()) || req.transactionId().startsWith("mock_tx_");
        return PaymentVerificationResult.builder()
                .success(ok)
                .transactionId(ok ? req.transactionId() : null)
                .failureReason(ok ? null : "MockGateway expected transactionId=MOCK_OK or mock_tx_*")
                .gatewayErrorCode(ok ? null : "MOCK_VERIFICATION_FAILED")
                .build();
    }

    @Override
    public WebhookVerificationResult verifyWebhook(String rawBody, String signatureHeader) {
        // Mock: signature is always trusted (this gateway is only used in
        // dev / smoke-test). The previous version returned paymentId=null
        // for every webhook, which made the markPaidByWebhook handler
        // immediately bail with IGNORED — meaning a tester / hand-fired
        // webhook against the mock gateway could never advance a payment
        // to PAID. Parse the body as JSON and accept either:
        //   {"paymentId": "..."}                          ← simplest mock shape
        //   {"payload": {"payment": {"entity": {"id": "..."}}}}  ← Razorpay-like
        // Empty / unparseable bodies still return null with a clear log
        // line so ops can spot a misconfigured webhook caller.
        String paymentId = null;
        String txnId = "mock_tx_" + UUID.randomUUID();
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                org.json.JSONObject body = new org.json.JSONObject(rawBody);
                if (body.has("paymentId")) {
                    paymentId = body.optString("paymentId", null);
                } else if (body.has("payload")) {
                    paymentId = body.getJSONObject("payload")
                            .getJSONObject("payment")
                            .getJSONObject("entity")
                            .optString("id", null);
                }
            } catch (Exception ignored) {
                log.info("MockPaymentGateway.verifyWebhook: body not parseable as JSON, returning paymentId=null");
            }
        }
        return new WebhookVerificationResult(true, paymentId, txnId, null);
    }

    /**
     * Mock VPA validation. Accepts anything that looks like {@code user@psp}
     * and returns a deterministic stub name derived from the local part —
     * which is enough for the frontend's live-preview UX to be exercised
     * end-to-end in dev without burning a real Razorpay call.
     *
     * <p>Returns {@code valid=false} for empty/malformed inputs so the
     * frontend's error-state branch is also testable locally.
     */
    @Override
    public VpaValidationResult validateVpa(String vpa) {
        if (vpa == null || !VPA_FORMAT_RE.matcher(vpa).matches()) {
            return VpaValidationResult.builder()
                    .valid(false)
                    .vpa(vpa)
                    .failureReason("Invalid UPI ID format")
                    .gatewayName(NAME)
                    .build();
        }
        // Derive a plausible "holder name" from the local part — e.g.
        // "anirudh.kumar@oksbi" → "ANIRUDH KUMAR". Good enough that
        // engineers can eyeball the UI flow without a real gateway.
        String local = vpa.substring(0, vpa.indexOf('@'));
        String stubName = local
                .replaceAll("[._\\-]+", " ")
                .toUpperCase()
                .trim();
        log.info("MockPaymentGateway.validateVpa vpa={} -> name={}", vpa, stubName);
        return VpaValidationResult.builder()
                .valid(true)
                .vpa(vpa)
                .customerName(stubName)
                .gatewayName(NAME)
                .build();
    }

    /** Same regex the controller / frontend use — single source of truth. */
    private static final java.util.regex.Pattern VPA_FORMAT_RE =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9.\\-]{1,63}$");

    /**
     * Builds an app-aware UPI intent URI. When the tenant picks GPay /
     * PhonePe / Paytm in the UI, we generate the app-specific deep link
     * so on Android the OS opens that app directly instead of the chooser.
     * Falls back to the universal upi:// scheme for OTHER / WHATSAPP.
     *
     *   GPay      tez://upi/pay?...
     *   PhonePe   phonepe://pay?...
     *   Paytm     paytmmp://pay?...
     *   BHIM      bhim://upi/pay?...
     *   AmazonPay amazonpay://pay?...
     *   CRED      cred://upi/pay?...
     *   else      upi://pay?...
     */
    private String buildUpiIntent(UpiApp app, String vpa, double amount, String txnRef) {
        String query = "pa=" + vpa
                     + "&pn=Home%20Rental"
                     + "&am=" + amount
                     + "&cu=INR"
                     + "&tn=Rent%20payment"
                     + "&tr=" + txnRef;
        return switch (app == null ? UpiApp.OTHER : app) {
            case GPAY      -> "tez://upi/pay?" + query;
            case PHONEPE   -> "phonepe://pay?" + query;
            case PAYTM     -> "paytmmp://pay?" + query;
            case BHIM      -> "bhim://upi/pay?" + query;
            case AMAZONPAY -> "amazonpay://pay?" + query;
            case CRED      -> "cred://upi/pay?" + query;
            case WHATSAPP, OTHER -> "upi://pay?" + query;
        };
    }
}
