package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.CreatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InvoiceResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.ReceiptResponse;
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

    @Operation(summary = "Create a payment record manually (publishes payment.created)")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest body) {
        log.info("POST /payments tenant={} flat={} amount={}", body.tenantId(), body.flatId(), body.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(body));
    }

    @Operation(summary = "List all payments (paginated)")
    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(paymentService.getAllPayments(pageable));
    }

    @Operation(summary = "Get a payment by id")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @Operation(summary = "List payments for a tenant")
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<PaymentResponse>> byTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(paymentService.getPaymentsByTenant(tenantId));
    }

    @Operation(summary = "List payments for an owner")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<PaymentResponse>> byOwner(@PathVariable String ownerId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOwner(ownerId));
    }

    @Operation(summary = "List all currently overdue payments")
    @GetMapping("/overdue")
    public ResponseEntity<List<PaymentResponse>> overdue() {
        return ResponseEntity.ok(paymentService.getOverduePayments());
    }

    @Operation(summary = "Owner records a cash payment manually (publishes payment.completed)")
    @PostMapping(value = "/{id}/pay-cash", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> payCash(@PathVariable String id,
                                                   @Valid @RequestBody PayCashRequest body) {
        return ResponseEntity.ok(paymentService.payCash(id, body));
    }

    @Operation(summary = "Get the invoice generated for a payment")
    @GetMapping("/{id}/invoice")
    public ResponseEntity<InvoiceResponse> invoice(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getInvoice(id));
    }

    @Operation(summary = "Get the receipt generated after a payment is captured")
    @GetMapping("/{id}/receipt")
    public ResponseEntity<ReceiptResponse> receipt(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getReceipt(id));
    }

    @Operation(summary = "Download the invoice PDF for a payment")
    @GetMapping(value = "/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoicePdf(@PathVariable String id) {
        byte[] pdf = paymentService.getInvoicePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "invoice-" + id + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @Operation(summary = "Download the receipt PDF for a captured payment")
    @GetMapping(value = "/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receiptPdf(@PathVariable String id) {
        byte[] pdf = paymentService.getReceiptPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "receipt-" + id + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
