package com.spa.home_rental_application.payment_service.payment_service.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.config.CashfreeProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.entities.VendorApiCall;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentGatewayException;
import com.spa.home_rental_application.payment_service.payment_service.service.VendorUsageRecorder;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Cashfree Payment Gateway (Easy Split ready).
 *
 * <p>Implements the {@link PaymentGateway} strategy on top of Cashfree's
 * PG v2023-08-01 API. Handles the three lifecycle steps we need today:
 *
 * <ol>
 *   <li>{@link #initiate} — {@code POST /pg/orders} to mint a
 *       {@code payment_session_id}. Frontend opens Cashfree Checkout
 *       SDK with that id + the merchant's app id. Money never touches
 *       our servers.</li>
 *   <li>{@link #verify} — {@code GET /pg/orders/{orderId}} + the
 *       {@code /payments} sibling to confirm the tenant's return-from-
 *       checkout is genuine and the order actually moved to PAID.</li>
 *   <li>{@link #verifyWebhook} — HMAC-SHA256 verifies the
 *       {@code x-webhook-signature} header against
 *       {@code timestamp + rawBody} using the webhook secret. Any
 *       async status transition (PAID / FAILED / DROPPED) arrives here.</li>
 * </ol>
 *
 * <p>The Phase-3 scaffolding stops short of populating the vendor
 * {@code order_splits} array — Phase 5 wires
 * {@code CommissionService.computePlatformFee} + owner
 * {@code cashfree_vendor_id} lookup and passes the vendor split JSON to
 * {@code /pg/orders} at initiate time. Today the order is a plain
 * (non-split) order so we can smoke-test the checkout flow before
 * adding the owner-registration + split leg.
 */
@Slf4j
public class CashfreePaymentGateway implements PaymentGateway {

    public static final String NAME = "cashfree";

    private final CashfreeProperties props;
    private final HttpClient http;
    private final ObjectMapper json;
    /**
     * Fire-and-forget vendor-call audit sink. Nullable so tests can
     * pass {@code null} instead of wiring the full JPA graph.
     */
    private final VendorUsageRecorder usageRecorder;

    public CashfreePaymentGateway(CashfreeProperties props,
                                   VendorUsageRecorder usageRecorder) {
        this.props = props;
        this.usageRecorder = usageRecorder;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.json = new ObjectMapper();
        log.info("CashfreePaymentGateway ready — environment={} baseUrl={} credentialsConfigured={}",
                props.getEnvironment(),
                props.baseUrl(),
                props.credentialsConfigured());
    }

    @Override
    public String name() { return NAME; }

    /* ------------------------- initiate ------------------------- */

    @Override
    public PaymentInitiationResult initiate(Payment payment, InitiatePaymentRequest req) {
        if (!props.credentialsConfigured()) {
            throw new PaymentGatewayException("Cashfree credentials not configured — "
                    + "set CASHFREE_APP_ID + CASHFREE_SECRET_KEY env vars");
        }

        String orderId = "hra_" + payment.getId();
        String returnUrl = req.returnUrl() == null || req.returnUrl().isBlank()
                ? "https://anirudhhomes.in/app/payments/" + payment.getId() + "/return"
                : req.returnUrl();

        // Cashfree needs a customer identifier + phone. The tenant's
        // auth id is stable per user (safe key for their PG account);
        // phone comes from the payment row's owner-facing metadata or
        // a stub if we don't have one — the sandbox is happy with any
        // 10-digit E.164-ish string.
        String customerId = payment.getTenantId() == null
                ? "anon_" + payment.getId()
                : payment.getTenantId();
        Map<String, Object> customer = Map.of(
                "customer_id",    customerId,
                "customer_email", "tenant+" + customerId + "@anirudhhomes.in",
                "customer_phone", "9999999999"
        );

        Map<String, Object> orderMeta = Map.of("return_url", returnUrl);

        // Build the request body. Fixed keys go in an ordered map so we
        // can conditionally include order_splits[] only when the caller
        // (PaymentServiceImpl.initiate) has stamped a vendor id +
        // platformFee. When absent, this is a plain non-split order.
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("order_id",         orderId);
        body.put("order_amount",     payment.getTotalAmount());
        body.put("order_currency",   "INR");
        body.put("customer_details", customer);
        body.put("order_meta",       orderMeta);
        body.put("order_note", payment.getSourceType() == null
                ? "Rent payment"
                : payment.getSourceType());

        String vendorId = payment.getOwnerVendorId();
        java.math.BigDecimal platformFee = payment.getPlatformFee() == null
                ? java.math.BigDecimal.ZERO
                : payment.getPlatformFee();
        if (vendorId != null && !vendorId.isBlank()) {
            java.math.BigDecimal vendorShare = payment.getTotalAmount().subtract(platformFee);
            if (vendorShare.signum() < 0) {
                throw new PaymentGatewayException(
                        "platformFee (" + platformFee + ") exceeds totalAmount ("
                                + payment.getTotalAmount() + ") for payment " + payment.getId());
            }
            body.put("order_splits", java.util.List.of(
                    Map.of("vendor_id", vendorId, "amount", vendorShare)));
            log.info("Cashfree order will split: total={} vendor={} share={} platformFee={}",
                    payment.getTotalAmount(), vendorId, vendorShare, platformFee);
        }

        JsonNode resp;
        try {
            resp = post("/orders", body,
                    "CASHFREE_ORDER_CREATE",
                    payment.getTenantId());
        } catch (PaymentGatewayException ex) {
            // Cashfree rejects duplicate order_ids with 409 order_already_exists.
            // This happens when the tenant clicks Pay again on the same
            // Payment row (same UUID → same "hra_{id}" order_id) after a
            // prior initiate succeeded but the checkout tab was closed /
            // the redirect was CSP-blocked before payment completed.
            // Fall back to GET /orders/{id} so the retry resumes the same
            // Cashfree order instead of dead-ending on a 502. Any other
            // gateway error re-throws unchanged.
            if (ex.getMessage() != null && ex.getMessage().contains("order_already_exists")) {
                log.info("Cashfree order {} already exists — resuming existing session on retry",
                        orderId);
                resp = get("/orders/" + orderId,
                        "CASHFREE_ORDER_CREATE_RETRY",
                        payment.getTenantId());
            } else {
                throw ex;
            }
        }
        String cfOrderId       = resp.path("cf_order_id").asText(null);
        String paymentSessionId = resp.path("payment_session_id").asText(null);

        if (paymentSessionId == null || paymentSessionId.isBlank()) {
            throw new PaymentGatewayException(
                    "Cashfree /orders returned no payment_session_id: " + resp);
        }

        log.info("Cashfree order created paymentId={} cfOrderId={} sessionId={}",
                payment.getId(), cfOrderId, mask(paymentSessionId));

        return PaymentInitiationResult.builder()
                .gatewayName(NAME)
                .gatewayOrderId(cfOrderId)
                .paymentSessionId(paymentSessionId)
                .publicKeyId(props.getAppId())
                .build();
    }

    /* ------------------------- verify ------------------------- */

    /**
     * Cross-check the tenant's return-from-checkout against Cashfree.
     * Never trusts the client — always fetches the order status from
     * the Cashfree API. Anything other than {@code order_status=PAID}
     * is a verification failure.
     */
    @Override
    public PaymentVerificationResult verify(Payment payment, VerifyPaymentRequest req) {
        String orderId = req.gatewayOrderId() != null && !req.gatewayOrderId().isBlank()
                ? req.gatewayOrderId()
                : "hra_" + payment.getId();

        JsonNode order = get("/orders/" + orderId,
                "CASHFREE_ORDER_VERIFY",
                payment.getTenantId());
        String status = order.path("order_status").asText("");

        if (!"PAID".equalsIgnoreCase(status)) {
            return PaymentVerificationResult.builder()
                    .success(false)
                    .failureReason("Cashfree order status is " + status + ", not PAID")
                    .gatewayErrorCode("ORDER_NOT_PAID")
                    .build();
        }

        // Grab the actual cf_payment_id for the audit trail — it's what
        // shows up on the tenant's bank statement / UPI ref.
        JsonNode payments = get("/orders/" + orderId + "/payments",
                "CASHFREE_PAYMENT_LOOKUP",
                payment.getTenantId());
        String cfPaymentId = null;
        if (payments.isArray() && payments.size() > 0) {
            for (JsonNode p : payments) {
                if ("SUCCESS".equalsIgnoreCase(p.path("payment_status").asText())) {
                    cfPaymentId = p.path("cf_payment_id").asText(null);
                    break;
                }
            }
        }

        return PaymentVerificationResult.builder()
                .success(true)
                .transactionId(cfPaymentId != null ? cfPaymentId : orderId)
                .build();
    }

    /* ------------------------- vendor registration ------------------------- */

    /**
     * Register an owner as a Cashfree Easy Split vendor.
     *
     * <p>Wraps {@code POST /pg/easy-split/vendors}. Called by
     * {@code CashfreeVendorService} once both prereqs are met
     * (bank saved AND kyc verified). Cashfree accepts synchronously,
     * runs its penny-drop asynchronously, and returns
     * {@code status: IN_BANK_VALIDATION} on the way to
     * {@code ACTIVE} — the caller stores that transitional state on
     * the local {@code cashfree_vendors} row and falls back to
     * direct-UPI for tenants of this owner until it flips ACTIVE.
     *
     * <p>{@link CashfreeVendorRegistrationRequest#businessType} defaults
     * to "Miscellaneous" — the only sandbox-safe value; see the
     * cashfree-api-quirks memory for why. Change to "Real Estate" or
     * "Rentals" ONLY after retesting against production, where the
     * full advertised list appears to actually be honored.
     */
    public CashfreeVendorRegistrationResult registerVendor(CashfreeVendorRegistrationRequest req) {
        if (!props.credentialsConfigured()) {
            throw new PaymentGatewayException("Cashfree credentials not configured");
        }
        Map<String, Object> body = Map.of(
                "vendor_id",        req.vendorId(),
                "status",           "ACTIVE",
                "name",             req.name(),
                "email",            req.email(),
                "phone",            req.phone(),
                "verify_account",   true,
                "dashboard_access", false,
                "bank", Map.of(
                        "account_number",  req.bankAccountNumber(),
                        "account_holder",  req.bankAccountHolder(),
                        "ifsc",            req.bankIfsc()
                ),
                "kyc_details", Map.of(
                        "account_type",  "INDIVIDUAL",
                        "business_type", req.businessType() == null ? "Miscellaneous" : req.businessType(),
                        "pan",           req.pan()
                )
        );
        JsonNode resp = post("/easy-split/vendors", body,
                "CASHFREE_VENDOR_CREATE", req.vendorId());
        String returnedVendorId = resp.path("vendor_id").asText(req.vendorId());
        String status = resp.path("status").asText("UNKNOWN");
        log.info("Cashfree vendor registered vendorId={} status={}", returnedVendorId, status);
        return new CashfreeVendorRegistrationResult(returnedVendorId, status);
    }

    /**
     * Update an existing Cashfree Easy Split vendor's bank details
     * (and optionally name / email / phone). Wraps
     * {@code PATCH /easy-split/vendors/{vendor_id}}.
     *
     * <p>Called by {@code CashfreeVendorServiceImpl} when a Kafka
     * event reports the user's bank has changed AFTER the vendor is
     * already ACTIVE. Without this, editing bank details in the
     * profile UI silently diverges from the destination Cashfree
     * routes money to — a real payout-integrity bug the moment we
     * hit production.
     *
     * <p>Cashfree re-runs their penny-drop against the new bank, so
     * the vendor transitions back to {@code IN_BANK_VALIDATION}
     * before it re-activates. Caller stores that transitional state
     * so the tenant pay page correctly falls back to direct-UPI in
     * the meantime.
     */
    public CashfreeVendorRegistrationResult updateVendor(CashfreeVendorRegistrationRequest req) {
        if (!props.credentialsConfigured()) {
            throw new PaymentGatewayException("Cashfree credentials not configured");
        }
        // Cashfree's PATCH accepts a subset — bank is the field we
        // actually care about, plus contact fields in case the user
        // also updated those. Vendor id, KYC, and status are
        // immutable via this endpoint.
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (req.name() != null && !req.name().isBlank())   body.put("name",  req.name());
        if (req.email() != null && !req.email().isBlank()) body.put("email", req.email());
        if (req.phone() != null && !req.phone().isBlank()) body.put("phone", req.phone());
        body.put("bank", Map.of(
                "account_number",  req.bankAccountNumber(),
                "account_holder",  req.bankAccountHolder(),
                "ifsc",            req.bankIfsc()
        ));
        body.put("verify_account", true);

        JsonNode resp = patch("/easy-split/vendors/" + req.vendorId(), body,
                "CASHFREE_VENDOR_UPDATE", req.vendorId());
        String returnedVendorId = resp.path("vendor_id").asText(req.vendorId());
        String status = resp.path("status").asText("IN_BANK_VALIDATION");
        log.info("Cashfree vendor updated vendorId={} newStatus={}", returnedVendorId, status);
        return new CashfreeVendorRegistrationResult(returnedVendorId, status);
    }

    /** Inbound to {@link #registerVendor}. Kept in this file to avoid DTO sprawl. */
    public record CashfreeVendorRegistrationRequest(
            String vendorId,
            String name,
            String email,
            String phone,
            String pan,
            String bankAccountNumber,
            String bankAccountHolder,
            String bankIfsc,
            /** Nullable — defaults to "Miscellaneous". */
            String businessType
    ) {}

    /** Response from {@link #registerVendor}. */
    public record CashfreeVendorRegistrationResult(
            String cashfreeVendorId,
            /** IN_BANK_VALIDATION | ACTIVE | REJECTED — Cashfree's own status string. */
            String status
    ) {}

    /* ------------------------- webhook ------------------------- */

    /**
     * Verifies Cashfree's webhook signature. Their scheme:
     * <pre>
     *   sig = base64( HMAC-SHA256( timestamp + raw_body,  webhook_secret ) )
     * </pre>
     * where {@code timestamp} is the {@code x-webhook-timestamp} header
     * value and {@code sig} is the {@code x-webhook-signature} header
     * value.
     *
     * <p>The {@code signatureHeader} arg is passed by the controller as a
     * pipe-joined {@code "signature|timestamp"} string so this method
     * stays inside the interface's two-arg shape without a schema
     * refactor. Ugly but surgical.
     */
    @Override
    public WebhookVerificationResult verifyWebhook(String rawBody, String signatureHeader) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            log.warn("Cashfree webhook received but no webhook_secret configured — refusing");
            return new WebhookVerificationResult(false, null, null, "WEBHOOK_SECRET_NOT_SET");
        }
        if (signatureHeader == null || !signatureHeader.contains("|")) {
            return new WebhookVerificationResult(false, null, null, "MALFORMED_SIGNATURE_HEADER");
        }
        String[] parts = signatureHeader.split("\\|", 2);
        String sig = parts[0];
        String ts  = parts[1];

        String computed;
        try {
            computed = hmacSha256Base64(ts + rawBody, props.getWebhookSecret());
        } catch (Exception ex) {
            log.warn("Cashfree webhook HMAC compute failed: {}", ex.toString());
            return new WebhookVerificationResult(false, null, null, "HMAC_COMPUTE_FAILED");
        }
        if (!constantTimeEq(sig, computed)) {
            log.warn("Cashfree webhook signature mismatch");
            return new WebhookVerificationResult(false, null, null, "SIGNATURE_MISMATCH");
        }

        // Signature good — parse the payload for the paymentId + txn id.
        // Cashfree ships webhook events with type + data.order + data.payment.
        try {
            JsonNode root = json.readTree(rawBody);
            String cfOrderId = root.path("data").path("order").path("order_id").asText(null);
            String cfPaymentId = root.path("data").path("payment").path("cf_payment_id").asText(null);
            // Our payment id is embedded in the order_id via the "hra_" prefix
            // we set in initiate(). Strip it back out so the caller can look
            // up the local Payment row.
            String localPaymentId = cfOrderId != null && cfOrderId.startsWith("hra_")
                    ? cfOrderId.substring(4)
                    : null;
            return new WebhookVerificationResult(true, localPaymentId, cfPaymentId, null);
        } catch (Exception ex) {
            log.warn("Cashfree webhook body unparseable: {}", ex.toString());
            return new WebhookVerificationResult(true, null, null, "BODY_UNPARSEABLE");
        }
    }

    /* ------------------------- helpers ------------------------- */

    private JsonNode post(String path, Object body, String vendorTag, String triggeredByUserId) {
        long startMs = System.currentTimeMillis();
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest req = baseRequest(path)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return checkOk(resp, path, vendorTag, triggeredByUserId, startMs);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cashfree POST {} failed", path, e);
            recordUsage(vendorTag, "POST " + path, VendorApiCall.Status.OUTAGE,
                    "TRANSPORT_ERROR", e.getMessage(),
                    (int) (System.currentTimeMillis() - startMs), triggeredByUserId);
            throw new PaymentGatewayException("Cashfree POST " + path + " failed: " + e.getMessage());
        }
    }

    private JsonNode patch(String path, Object body, String vendorTag, String triggeredByUserId) {
        long startMs = System.currentTimeMillis();
        try {
            String payload = json.writeValueAsString(body);
            // Java's HttpClient doesn't have a first-class .PATCH()
            // builder — go via .method("PATCH", ...) which is the
            // documented workaround since JDK 11.
            HttpRequest req = baseRequest(path)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return checkOk(resp, path, vendorTag, triggeredByUserId, startMs);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cashfree PATCH {} failed", path, e);
            recordUsage(vendorTag, "PATCH " + path, VendorApiCall.Status.OUTAGE,
                    "TRANSPORT_ERROR", e.getMessage(),
                    (int) (System.currentTimeMillis() - startMs), triggeredByUserId);
            throw new PaymentGatewayException("Cashfree PATCH " + path + " failed: " + e.getMessage());
        }
    }

    private JsonNode get(String path, String vendorTag, String triggeredByUserId) {
        long startMs = System.currentTimeMillis();
        try {
            HttpRequest req = baseRequest(path).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return checkOk(resp, path, vendorTag, triggeredByUserId, startMs);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cashfree GET {} failed", path, e);
            recordUsage(vendorTag, "GET " + path, VendorApiCall.Status.OUTAGE,
                    "TRANSPORT_ERROR", e.getMessage(),
                    (int) (System.currentTimeMillis() - startMs), triggeredByUserId);
            throw new PaymentGatewayException("Cashfree GET " + path + " failed: " + e.getMessage());
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(props.baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("x-client-id",   props.getAppId())
                .header("x-client-secret", props.getSecretKey())
                .header("x-api-version", props.getApiVersion())
                .header("Accept", "application/json");
    }

    private JsonNode checkOk(HttpResponse<String> resp, String path,
                             String vendorTag, String triggeredByUserId, long startMs) throws Exception {
        int code = resp.statusCode();
        int elapsed = (int) (System.currentTimeMillis() - startMs);
        if (code / 100 != 2) {
            log.warn("Cashfree {} returned HTTP {} body={}", path, code, resp.body());
            VendorApiCall.Status status;
            if (code == 401 || code == 403) status = VendorApiCall.Status.UNAUTHORIZED;
            else if (code == 402 || code == 429) status = VendorApiCall.Status.BILLING_ALERT;
            else if (code >= 500) status = VendorApiCall.Status.OUTAGE;
            else status = VendorApiCall.Status.USER_ERROR;
            recordUsage(vendorTag, resp.request().method() + " " + path, status,
                    "HTTP_" + code, safeTruncate(resp.body(), 200),
                    elapsed, triggeredByUserId);
            throw new PaymentGatewayException(
                    "Cashfree " + path + " → HTTP " + code + ": " + safeTruncate(resp.body(), 500));
        }
        recordUsage(vendorTag, resp.request().method() + " " + path,
                VendorApiCall.Status.SUCCESS, null, null, elapsed, triggeredByUserId);
        return json.readTree(resp.body());
    }

    /** Null-safe forward to the (optional) usage recorder. */
    private void recordUsage(String vendorTag, String endpoint,
                             VendorApiCall.Status status, String errorCode,
                             String errorMessage, Integer responseTimeMs,
                             String triggeredByUserId) {
        if (usageRecorder == null) return;
        usageRecorder.record(vendorTag, endpoint, status, errorCode,
                errorMessage, responseTimeMs, triggeredByUserId);
    }

    private static String hmacSha256Base64(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(out);
    }

    /** Byte-wise compare so timing side-channels don't leak the secret. */
    private static boolean constantTimeEq(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String mask(String s) {
        if (s == null || s.length() < 8) return "***";
        return s.substring(0, 6) + "…" + s.substring(s.length() - 4);
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // Silence "unused import" — we may need Locale for future casing helpers.
    @SuppressWarnings("unused") private static final Locale LOCALE = Locale.ROOT;
}
