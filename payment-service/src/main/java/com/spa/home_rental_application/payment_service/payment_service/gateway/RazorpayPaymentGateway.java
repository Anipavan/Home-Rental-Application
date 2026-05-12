package com.spa.home_rental_application.payment_service.payment_service.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.config.RazorpayProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.UpiApp;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Real Razorpay integration. Wires:
 *   - {@link #initiate} -> calls Razorpay's Orders API via the official
 *     razorpay-java SDK to create a server-side order; returns the
 *     {@code order_id} the SPA needs to launch the checkout / UPI intent.
 *   - {@link #verify}   -> validates Razorpay's HMAC-SHA256 signature
 *     (orderId|paymentId, key_secret) on the success callback.
 *   - {@link #verifyWebhook} -> validates X-Razorpay-Signature on the
 *     webhook body and parses out paymentId + orderId from the JSON.
 *
 * UPI app-aware deep links: when the tenant picks GPay / PhonePe / Paytm,
 * we generate a UPI intent that opens directly in that app instead of
 * the OS chooser. Standard upi:// fallback when the app is OTHER or
 * unspecified.
 *
 * Activate at runtime with:
 *   PAYMENT_GATEWAY=razorpay
 *   APP_RAZORPAY_KEY_ID=rzp_test_xxx          (sandbox)
 *   APP_RAZORPAY_KEY_SECRET=xxx
 *   APP_RAZORPAY_WEBHOOK_SECRET=xxx
 */
@Slf4j
public class RazorpayPaymentGateway implements PaymentGateway {

    public static final String NAME = "razorpay";
    private static final HexFormat HEX = HexFormat.of();

    private final RazorpayProperties props;
    private final RazorpayClient client;
    private final PaymentRepository paymentRepository;

    public RazorpayPaymentGateway(RazorpayProperties props, PaymentRepository paymentRepository) {
        this.props = props;
        this.paymentRepository = paymentRepository;
        try {
            this.client = new RazorpayClient(props.getKeyId(), props.getKeySecret());
            log.info("RazorpayPaymentGateway: initialized with keyId={}", props.getKeyId());
        } catch (RazorpayException ex) {
            throw new IllegalStateException(
                    "Could not initialize Razorpay client -- check APP_RAZORPAY_KEY_ID / KEY_SECRET", ex);
        }
    }

    @Override
    public String name() { return NAME; }

    @Override
    public PaymentInitiationResult initiate(Payment payment, InitiatePaymentRequest req) {
        log.info("RazorpayPaymentGateway.initiate paymentId={} method={} app={}",
                payment.getId(), req.paymentMethod(), req.upiApp());

        // Razorpay wants amount in the smallest currency unit (paise).
        int amountPaise = payment.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValueExact();

        String orderId;
        try {
            JSONObject orderRequest = new JSONObject()
                    .put("amount", amountPaise)
                    .put("currency", "INR")
                    .put("receipt", payment.getId())
                    .put("notes", new JSONObject(Map.of(
                            "flatId",   String.valueOf(payment.getFlatId()),
                            "tenantId", String.valueOf(payment.getTenantId()),
                            "ownerId",  String.valueOf(payment.getOwnerId()))));
            Order order = client.orders.create(orderRequest);
            orderId = order.get("id");
            log.info("Razorpay Orders.create -> orderId={} for paymentId={}", orderId, payment.getId());
        } catch (RazorpayException ex) {
            log.error("Razorpay Orders.create failed for paymentId={}", payment.getId(), ex);
            throw new IllegalStateException("Razorpay order creation failed: " + ex.getMessage(), ex);
        }

        var b = PaymentInitiationResult.builder()
                .gatewayName(NAME)
                .gatewayOrderId(orderId);

        switch (req.paymentMethod()) {
            case UPI -> {
                String vpa = req.upiVpa() != null && !req.upiVpa().isBlank()
                        ? req.upiVpa() : "rent@razorpay";
                b.upiCollectStatus("PENDING_USER_ACTION")
                 .upiIntentUrl(buildUpiIntent(req.upiApp(), vpa,
                         payment.getTotalAmount().doubleValue(), orderId));
            }
            case CARD, NET_BANKING, WALLET ->
                    // Razorpay's hosted Checkout. The SPA can also load the
                    // Razorpay JS Checkout widget client-side using key_id +
                    // order_id; either way the same orderId binds the call.
                    b.redirectUrl("https://api.razorpay.com/v1/checkout/embedded?key_id="
                            + props.getKeyId() + "&order_id=" + orderId);
            case BANK_TRANSFER ->
                    // Real Razorpay uses Smart Collect virtual accounts here;
                    // a separate API not modelled in this stub.
                    b.bankAccountNumber("RZRX" + orderId.substring(orderId.length() - 8))
                     .bankIfsc("RAZR0000001")
                     .bankAccountName("Home Rental");
            case CASH -> { /* /pay-cash bypasses the gateway entirely */ }
        }
        return b.build();
    }

    @Override
    public PaymentVerificationResult verify(Payment payment, VerifyPaymentRequest req) {
        // Razorpay's standard signature for client callback:
        //   HMAC-SHA256( razorpay_order_id + "|" + razorpay_payment_id, key_secret )
        String expected = hmac(req.gatewayOrderId() + "|" + req.transactionId(),
                props.getKeySecret());
        boolean ok = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                req.signature().getBytes(StandardCharsets.UTF_8));

        return PaymentVerificationResult.builder()
                .success(ok)
                .transactionId(ok ? req.transactionId() : null)
                .failureReason(ok ? null : "Signature mismatch")
                .gatewayErrorCode(ok ? null : "RAZORPAY_SIGNATURE_MISMATCH")
                .build();
    }

    @Override
    public WebhookVerificationResult verifyWebhook(String rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null) {
            return new WebhookVerificationResult(false, null, null, "Missing body or signature");
        }
        String expected = hmac(rawBody, props.getWebhookSecret());
        boolean ok = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            return new WebhookVerificationResult(false, null, null, "Webhook signature mismatch");
        }

        // Audit H25: Razorpay sends a payload like:
        //   { event: "payment.captured",
        //     payload: { payment: { entity: { id: "pay_xxx", order_id: "order_xxx", ... } } } }
        //
        // The previous code (incorrectly) used `order_id` as our local
        // paymentId — but that's Razorpay's gateway order id, NOT our
        // ID. The lookup-by-paymentId in PaymentServiceImpl always
        // failed silently and the payment never got credited.
        //
        // Correct: return the Razorpay order_id verbatim. The webhook
        // handler caller resolves it to a local paymentId via
        // {@code paymentRepository.findByGatewayOrderId}, which is
        // stamped onto the Payment row at initiate-time. {@code id}
        // (Razorpay's payment id) is the transactionId we record.
        try {
            JSONObject body = new JSONObject(rawBody);
            JSONObject pmt = body.getJSONObject("payload")
                                  .getJSONObject("payment")
                                  .getJSONObject("entity");
            String gatewayOrderId = pmt.optString("order_id", null);
            String txnId          = pmt.optString("id",       null);
            String localPaymentId = paymentRepository.findByGatewayOrderId(gatewayOrderId)
                    .map(com.spa.home_rental_application.payment_service.payment_service.entities.Payment::getId)
                    .orElse(null);
            if (localPaymentId == null) {
                log.warn("Razorpay webhook for unknown gatewayOrderId={} (no local payment matched)",
                        gatewayOrderId);
            }
            return new WebhookVerificationResult(true, localPaymentId, txnId, null);
        } catch (Exception ex) {
            log.warn("Razorpay webhook signature OK but body parse failed: {}", ex.getMessage());
            return new WebhookVerificationResult(true, null, null, null);
        }
    }

    /* ----------------------- helpers ----------------------- */

    /**
     * Builds an app-specific UPI intent so picking GPay/PhonePe/Paytm in
     * the UI opens directly in that app on Android instead of the OS
     * chooser. iOS uses the universal upi:// scheme regardless.
     */
    private static String buildUpiIntent(UpiApp app, String vpa, double amount, String txnRef) {
        String query = "pa=" + vpa
                     + "&pn=Home%20Rental"
                     + "&am=" + amount
                     + "&cu=INR"
                     + "&tn=Rent%20payment"
                     + "&tr=" + txnRef;
        // Each UPI app registers its own URI scheme on Android. Falling
        // back to the standard upi:// triggers the OS chooser.
        return switch (app == null ? UpiApp.OTHER : app) {
            case GPAY      -> "tez://upi/pay?" + query;        // Google Pay (legacy "Tez" scheme)
            case PHONEPE   -> "phonepe://pay?" + query;
            case PAYTM     -> "paytmmp://pay?" + query;
            case BHIM      -> "bhim://upi/pay?" + query;
            case AMAZONPAY -> "amazonpay://pay?" + query;
            case CRED      -> "cred://upi/pay?" + query;
            case WHATSAPP, OTHER -> "upi://pay?" + query;
        };
    }

    private static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC", ex);
        }
    }
}
