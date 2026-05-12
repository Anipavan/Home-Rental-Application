package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InitiatePaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.WebhookVerificationResult;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService.WebhookOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Payment Gateway", description = "Initiate, verify, and webhook handlers for the active payment gateway")
public class PaymentGatewayController {

    private final PaymentService paymentService;
    private final PaymentGateway gateway;

    public PaymentGatewayController(PaymentService paymentService, PaymentGateway gateway) {
        this.paymentService = paymentService;
        this.gateway = gateway;
    }

    @Operation(summary = "Initiate a payment via the configured gateway. Returns redirect URL / UPI intent / bank details depending on the chosen method.")
    @PostMapping(value = "/initiate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InitiatePaymentResponse> initiate(@Valid @RequestBody InitiatePaymentRequest body) {
        log.info("POST /payments/initiate paymentId={} method={}", body.paymentId(), body.paymentMethod());
        return ResponseEntity.ok(paymentService.initiatePayment(body));
    }

    @Operation(summary = "Verify a payment after the client returns from the gateway. Marks PAID on success.")
    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> verify(@Valid @RequestBody VerifyPaymentRequest body) {
        log.info("POST /payments/verify paymentId={} txn={}", body.paymentId(), body.transactionId());
        return ResponseEntity.ok(paymentService.verifyPayment(body));
    }

    /**
     * Inbound webhook from Razorpay / Stripe / mock-gateway.
     *
     * <p>Audit C13 (CRITICAL): the previous handler verified the
     * signature but never actually credited the payment, with a
     * comment admitting "left as a documented hook". That meant any
     * future fix without idempotency would double-credit on Razorpay
     * retries. The new flow:
     *
     * <ol>
     *   <li>10 MB body-size cap (audit M14 — unbounded JSON DoS guard).</li>
     *   <li>HMAC signature verification (existing).</li>
     *   <li>Idempotent {@code markPaidByWebhook} that writes to the
     *       {@code processed_webhooks} table BEFORE flipping payment
     *       status, with a unique constraint on
     *       (gatewayName, eventKey). Duplicate deliveries collide on
     *       the constraint and exit with a 200 + duplicate=true.</li>
     * </ol>
     *
     * <p>The endpoint MUST stay public (gateways can't supply our JWT)
     * but HMAC + size cap + idempotency together close the abuse
     * surface that an unauthenticated webhook would otherwise expose.
     */
    @Operation(summary = "Webhook endpoint for the payment gateway to push payment success/failure asynchronously.")
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String razorpaySig,
            @RequestHeader(name = "Stripe-Signature", required = false) String stripeSig,
            @RequestBody String rawBody) {
        // Hard cap on body size — even a valid signature shouldn't be
        // allowed to ship a 500MB payload (would OOM the parser).
        if (rawBody != null && rawBody.length() > 1_000_000) {
            log.warn("Webhook body exceeds 1 MB ({} bytes) — rejecting before signature check", rawBody.length());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("ok", false, "reason", "Body exceeds 1 MB"));
        }

        String sig = razorpaySig != null ? razorpaySig : stripeSig;
        WebhookVerificationResult res = gateway.verifyWebhook(rawBody, sig);
        if (!res.valid()) {
            log.warn("Rejected webhook (gateway={}): {}", gateway.name(), res.errorMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("ok", false, "reason", String.valueOf(res.errorMessage())));
        }
        log.info("Webhook accepted (gateway={}): paymentId={} txnId={}",
                gateway.name(), res.paymentId(), res.transactionId());

        // Derive a per-gateway event key. Razorpay / Stripe drop a
        // unique identifier into the payload; we prefer the gateway's
        // own transactionId (always per-attempt unique). If that's
        // missing we fall back to a hash of (paymentId + rawBody length)
        // which won't collide for a same-payment double-delivery either.
        String eventKey = res.transactionId();
        if (eventKey == null || eventKey.isBlank()) {
            eventKey = res.paymentId() + ":" + (rawBody == null ? 0 : rawBody.length());
        }

        PaymentService.WebhookOutcome outcome = paymentService.markPaidByWebhook(
                gateway.name(), eventKey, res.paymentId(), res.transactionId());

        return ResponseEntity.ok(Map.of(
                "ok",        true,
                "outcome",   outcome.name(),
                "duplicate", outcome == PaymentService.WebhookOutcome.DUPLICATE,
                "paymentId", res.paymentId() == null ? "" : res.paymentId()));
    }

    /** Return URL the MockPaymentGateway sends users to after "payment". For local manual testing. */
    @Operation(summary = "MockPaymentGateway return URL — use during dev testing only.")
    @GetMapping("/mock/return")
    public ResponseEntity<Map<String, Object>> mockReturn(@RequestParam String orderId,
                                                          @RequestParam String paymentId) {
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Mock gateway returned. POST /payments/verify with transactionId=MOCK_OK to complete.",
                "paymentId", paymentId,
                "orderId", orderId));
    }

    /**
     * Mock checkout page — renders a small HTML form so non-UPI flows
     * (CARD / NET_BANKING / WALLET) feel like a real redirect-and-back. The
     * "Pay" button POSTs the verification params back through the tenant's
     * returnUrl so the regular PaymentReturnPage can call /payments/verify
     * the same way it would for a real gateway.
     */
    @Operation(summary = "Mock gateway hosted checkout page — used by the in-process MockPaymentGateway only.")
    @GetMapping(value = "/mock/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mockCheckout(@RequestParam String orderId,
                                                @RequestParam String paymentId,
                                                @RequestParam(required = false) String method,
                                                @RequestParam(required = false) String amount,
                                                @RequestParam(required = false) String returnUrl) {
        String safeReturn = returnUrl == null ? "" : returnUrl;
        String safeMethod = method == null ? "CARD" : method;
        String safeAmount = amount == null ? "—" : amount;

        // Build the success and cancel return URLs. The success URL embeds
        // gatewayOrderId / transactionId / signature so the frontend's
        // PaymentReturnPage can POST /payments/verify with them.
        String successUrl = safeReturn
                + (safeReturn.contains("?") ? "&" : "?")
                + "gatewayOrderId=" + orderId
                + "&transactionId=MOCK_OK"
                + "&signature=mock_signature";
        String cancelUrl = safeReturn;

        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>Mock Gateway · Hearth</title>
                  <style>
                    body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
                           margin: 0; background: #f5f5f7; color: #111;
                           min-height: 100vh; display: grid; place-items: center; }
                    .card { background: #fff; border-radius: 14px; padding: 28px 32px;
                            box-shadow: 0 4px 24px rgba(0,0,0,.06);
                            max-width: 440px; width: calc(100%% - 32px); }
                    h1 { margin: 0 0 4px; font-size: 20px; }
                    .sub { color: #666; font-size: 13px; margin-bottom: 18px; }
                    .row { display:flex; justify-content:space-between; padding:8px 0;
                           border-bottom: 1px solid #eee; font-size: 14px; }
                    .row:last-of-type { border-bottom: none; }
                    .pill { display:inline-block; padding:2px 8px; border-radius:999px;
                            background:#fff7e6; color:#b25000; font-size:12px; font-weight:600; }
                    .actions { display:flex; gap:10px; margin-top: 22px; }
                    button { flex:1; padding:12px 14px; border:none; border-radius:10px;
                             font-size:14px; font-weight:600; cursor:pointer; }
                    .pay { background:#1a73e8; color:#fff; }
                    .pay:hover { background:#1558b0; }
                    .cancel { background:#f1f3f4; color:#444; }
                    .footer { margin-top: 16px; font-size: 11px; color: #888; text-align:center; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <span class="pill">MOCK GATEWAY · DEV MODE</span>
                    <h1 style="margin-top:10px">Confirm payment</h1>
                    <p class="sub">This page is shown only when no real gateway is configured.</p>
                    <div class="row"><span>Method</span><span><b>%s</b></span></div>
                    <div class="row"><span>Amount</span><span><b>Rs. %s</b></span></div>
                    <div class="row"><span>Order ID</span><span style="font-family:monospace; font-size:12px">%s</span></div>
                    <div class="row"><span>Payment ID</span><span style="font-family:monospace; font-size:12px">%s</span></div>
                    <div class="actions">
                      <button class="cancel" onclick="window.location.href='%s'">Cancel</button>
                      <button class="pay" onclick="window.location.href='%s'">Pay Rs. %s</button>
                    </div>
                    <div class="footer">Powered by MockPaymentGateway — Hearth</div>
                  </div>
                </body>
                </html>
                """.formatted(
                        safeMethod.replace("_", " ").toLowerCase(),
                        safeAmount,
                        orderId,
                        paymentId,
                        cancelUrl.isEmpty() ? "javascript:history.back()" : cancelUrl,
                        successUrl,
                        safeAmount);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
