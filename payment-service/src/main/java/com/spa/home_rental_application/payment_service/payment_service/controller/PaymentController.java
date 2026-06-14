package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.CreatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InvoiceResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.ReceiptResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.UnpaidSummaryDTO;
import com.spa.home_rental_application.payment_service.payment_service.security.CallerSecurity;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Payments", description = "Rent invoices, payments, receipts")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /* ───────────────────── Authz model ─────────────────────
     *
     * Every endpoint below is gated by the gateway-stamped
     * X-Auth-User-Id header. The check happens in one of three flavours:
     *
     *  (1) requireSelfOrAdmin(targetId) — list endpoints where the path
     *      carries a userId (tenant or owner). Returns the list if the
     *      caller IS that user or is ADMIN.
     *  (2) requireTenantOwnerOrAdmin(tenant, owner) — per-payment
     *      endpoints where we first load the row, then check the
     *      caller is the tenant on it, the owner on it, or ADMIN.
     *  (3) requireAdmin() — admin-only endpoints (raw list, overdue
     *      cross-tenant report, manual create).
     *
     * Audit C5: previously NONE of these checks existed; the controller
     * accepted any path id from any logged-in user.
     */

    @Operation(summary = "Create a payment record manually (ADMIN only — owners use the scheduler-fed flow)")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Manual creation is an admin path — tenants must never be able
        // to mint a payment for themselves (would let them forge a
        // "PAID" record with zero gateway capture). Owners' rent-cycle
        // payments are created by PaymentSchedulerJob, not this endpoint.
        CallerSecurity.requireAdmin();
        // Audit M13: honour the optional Idempotency-Key header
        // (Stripe convention). Naive client retry mid-network-flap
        // would otherwise create duplicate Payment rows for the same
        // tenant + flat + dueDate. With the key, the service either
        // returns the previously-created Payment or generates a new
        // one + remembers the key.
        log.info("POST /payments tenant={} flat={} amount={} idempotencyKey={}",
                body.tenantId(), body.flatId(), body.amount(),
                idempotencyKey == null ? "-" : "set");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(body, idempotencyKey));
    }

    /**
     * Tenant-accessible Payment-row creator scoped to society maintenance
     * charges. Property-service calls this via Feign when a resident clicks
     * "Pay all" on /app/society/pay-all — the body describes the total
     * outstanding for the flat that month, and the returned paymentId
     * funnels through the SAME Razorpay flow as rent
     * (/app/payments/{id}/pay → /payments/initiate → 303 to gateway).
     *
     * <p>Authz: caller MUST be the tenant on the body (or admin). The
     * standard {@code create} endpoint above is admin-only because a
     * tenant minting their own Payment for rent could forge a PAID
     * record; here that risk doesn't apply — the row starts PENDING and
     * only flips PAID via the gateway-signed webhook, exactly like rent.
     *
     * <p>The Idempotency-Key header guards against fast double-clicks
     * on the "Pay all" button creating two Razorpay orders.
     */
    @Operation(summary = "Create a payment record for a society maintenance charge — tenant-accessible.")
    @PostMapping(value = "/society-charge", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> createForSocietyCharge(
            @Valid @RequestBody CreatePaymentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // The caller must be the tenant on the body (or admin). Without
        // this an arbitrary signed-in user could mint a Payment against
        // someone else's flat — they couldn't make it PAY without going
        // through the gateway, but they could clutter the target
        // tenant's payment list. Tight authz keeps the surface clean.
        CallerSecurity.requireTenantOwnerOrAdmin(body.tenantId(), body.ownerId());
        log.info("POST /payments/society-charge tenant={} flat={} amount={} idempotencyKey={}",
                body.tenantId(), body.flatId(), body.amount(),
                idempotencyKey == null ? "-" : "set");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(body, idempotencyKey));
    }

    @Operation(summary = "List all payments (ADMIN only, paginated)")
    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        // Cross-tenant listing is an admin-only surface — owners and
        // tenants must hit their scoped endpoints below instead.
        CallerSecurity.requireAdmin();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(paymentService.getAllPayments(pageable));
    }

    @Operation(summary = "Get a payment by id (tenant/owner/admin)")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable String id) {
        PaymentResponse p = paymentService.getPaymentById(id);
        CallerSecurity.requireTenantOwnerOrAdmin(p.tenantId(), p.ownerId());
        return ResponseEntity.ok(p);
    }

    @Operation(summary = "List payments for a tenant (self or admin)")
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<PaymentResponse>> byTenant(@PathVariable String tenantId) {
        CallerSecurity.requireSelfOrAdmin(tenantId);
        return ResponseEntity.ok(paymentService.getPaymentsByTenant(tenantId));
    }

    @Operation(summary = "List payments for an owner (self or admin)")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<PaymentResponse>> byOwner(@PathVariable String ownerId) {
        CallerSecurity.requireSelfOrAdmin(ownerId);
        return ResponseEntity.ok(paymentService.getPaymentsByOwner(ownerId));
    }

    /**
     * Outstanding-dues summary for a flat. Called by property-service
     * via Feign when validating a tenant-initiated vacate request
     * (Issue #5 — all PENDING + OVERDUE invoices must be cleared
     * before the vacate is scheduled). Also useful for any UI that
     * wants to render an "Outstanding ₹X" badge on a flat card.
     *
     * <p>No CallerSecurity gate — Eureka-routed Feign calls from
     * property-service don't carry a user JWT, and the endpoint is
     * read-only with no PII beyond a totals summary + invoice
     * numbers (no tenant identity is leaked).
     */
    @Operation(summary = "Outstanding (unpaid) summary for a flat — PENDING + OVERDUE invoices")
    @GetMapping("/flat/{flatId}/unpaid")
    public ResponseEntity<UnpaidSummaryDTO> unpaidByFlat(@PathVariable String flatId) {
        return ResponseEntity.ok(paymentService.getUnpaidByFlat(flatId));
    }

    @Operation(summary = "List all currently overdue payments (ADMIN only, paginated)")
    @GetMapping("/overdue")
    public ResponseEntity<Page<PaymentResponse>> overdue(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Audit L4: cap page size — same reasoning as M11.
            @RequestParam(defaultValue = "20") @Min(1) @jakarta.validation.constraints.Max(100) int size) {
        // Cross-tenant operational view — admins use this for collections
        // ops. Tenants/owners get their own overdue rows via the scoped
        // /tenant/{id} and /owner/{id} endpoints which filter status.
        CallerSecurity.requireAdmin();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(paymentService.getOverduePayments(pageable));
    }

    @Operation(summary = "Owner records a cash payment manually (owner of this payment or admin)")
    @PostMapping(value = "/{id}/pay-cash", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> payCash(@PathVariable String id,
                                                   @Valid @RequestBody PayCashRequest body) {
        PaymentResponse p = paymentService.getPaymentById(id);
        // Cash settlements are owner-only — tenants can't mark their own
        // rent as paid. Admins can override.
        CallerSecurity.requireSelfOrAdmin(p.ownerId());
        return ResponseEntity.ok(paymentService.payCash(id, body));
    }

    @Operation(summary = "Owner confirms a UPI / NEFT / IMPS payment received out-of-band (owner of this payment or admin)")
    @PostMapping(value = "/{id}/mark-upi-received", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> markUpiReceived(@PathVariable String id,
                                                           @Valid @RequestBody com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest body) {
        PaymentResponse p = paymentService.getPaymentById(id);
        // Same authz model as cash: only the owner sees money in
        // their bank, so only the owner can confirm receipt.
        CallerSecurity.requireSelfOrAdmin(p.ownerId());
        return ResponseEntity.ok(paymentService.markUpiReceived(id, body));
    }

    /**
     * Returns everything the tenant needs to pay rent directly to
     * the owner — owner's UPI VPA, a fully-formed UPI QR payload,
     * and a bank-transfer fallback (masked account + IFSC). Read
     * by the tenant payment page; gated to the tenant of THIS
     * invoice or the owner of THIS invoice or admin (matches the
     * /invoice + /receipt endpoints).
     */
    @Operation(summary = "Get the payout details for a payment (tenant of this invoice, owner, or admin)")
    @GetMapping("/{id}/payout-details")
    public ResponseEntity<com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PayoutDetailsResponse> payoutDetails(
            @PathVariable String id) {
        PaymentResponse p = paymentService.getPaymentById(id);
        CallerSecurity.requireTenantOwnerOrAdmin(p.tenantId(), p.ownerId());
        return ResponseEntity.ok(paymentService.getPayoutDetails(id));
    }

    @Operation(summary = "Get the invoice generated for a payment (tenant/owner/admin)")
    @GetMapping("/{id}/invoice")
    public ResponseEntity<InvoiceResponse> invoice(@PathVariable String id) {
        PaymentResponse p = paymentService.getPaymentById(id);
        CallerSecurity.requireTenantOwnerOrAdmin(p.tenantId(), p.ownerId());
        return ResponseEntity.ok(paymentService.getInvoice(id));
    }

    @Operation(summary = "Get the receipt generated after a payment is captured (tenant/owner/admin)")
    @GetMapping("/{id}/receipt")
    public ResponseEntity<ReceiptResponse> receipt(@PathVariable String id) {
        PaymentResponse p = paymentService.getPaymentById(id);
        CallerSecurity.requireTenantOwnerOrAdmin(p.tenantId(), p.ownerId());
        return ResponseEntity.ok(paymentService.getReceipt(id));
    }

    @Operation(summary = "Download the invoice PDF for a payment (tenant/owner/admin)")
    @GetMapping(value = "/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoicePdf(@PathVariable String id) {
        PaymentResponse p = paymentService.getPaymentById(id);
        CallerSecurity.requireTenantOwnerOrAdmin(p.tenantId(), p.ownerId());
        byte[] pdf = paymentService.getInvoicePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "invoice-" + id + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @Operation(summary = "Download the receipt PDF for a captured payment (tenant/owner/admin)")
    @GetMapping(value = "/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receiptPdf(@PathVariable String id) {
        PaymentResponse p = paymentService.getPaymentById(id);
        CallerSecurity.requireTenantOwnerOrAdmin(p.tenantId(), p.ownerId());
        byte[] pdf = paymentService.getReceiptPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "receipt-" + id + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
