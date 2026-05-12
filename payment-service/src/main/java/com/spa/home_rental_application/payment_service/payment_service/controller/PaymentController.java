package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.CreatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InvoiceResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.ReceiptResponse;
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
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest body) {
        // Manual creation is an admin path — tenants must never be able
        // to mint a payment for themselves (would let them forge a
        // "PAID" record with zero gateway capture). Owners' rent-cycle
        // payments are created by PaymentSchedulerJob, not this endpoint.
        CallerSecurity.requireAdmin();
        log.info("POST /payments tenant={} flat={} amount={}", body.tenantId(), body.flatId(), body.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(body));
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

    @Operation(summary = "List all currently overdue payments (ADMIN only)")
    @GetMapping("/overdue")
    public ResponseEntity<List<PaymentResponse>> overdue() {
        // Cross-tenant operational view — admins use this for collections
        // ops. Tenants/owners get their own overdue rows via the scoped
        // /tenant/{id} and /owner/{id} endpoints which filter status.
        CallerSecurity.requireAdmin();
        return ResponseEntity.ok(paymentService.getOverduePayments());
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
