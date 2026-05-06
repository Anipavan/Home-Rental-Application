package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InitiatePaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.WebhookVerificationResult;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
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

    @Operation(summary = "Webhook endpoint for the payment gateway to push payment success/failure asynchronously.")
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String razorpaySig,
            @RequestHeader(name = "Stripe-Signature", required = false) String stripeSig,
            @RequestBody String rawBody) {
        String sig = razorpaySig != null ? razorpaySig : stripeSig;
        WebhookVerificationResult res = gateway.verifyWebhook(rawBody, sig);
        if (!res.valid()) {
            log.warn("Rejected webhook (gateway={}): {}", gateway.name(), res.errorMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("ok", false, "reason", String.valueOf(res.errorMessage())));
        }
        log.info("Webhook accepted (gateway={}): paymentId={} txnId={}", gateway.name(), res.paymentId(), res.transactionId());
        // For brevity we don't auto-mark paid here — production webhook handler would lookup the payment by
        // gateway order id (parsed from rawBody) and call markPaid(). Left as a documented hook.
        return ResponseEntity.ok(Map.of("ok", true));
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
}
