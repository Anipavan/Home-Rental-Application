package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.*;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaymentService {

    /* --- lifecycle --- */
    PaymentResponse createPayment(CreatePaymentRequest dto);

    /**
     * Idempotency-Key aware overload (audit M13). Re-sending the same
     * key with the same body returns the previously-created payment
     * instead of inserting a duplicate. Key is null = no idempotency
     * (behaves identically to the single-arg overload).
     */
    PaymentResponse createPayment(CreatePaymentRequest dto, String idempotencyKey);
    PaymentResponse getPaymentById(String id);
    Page<PaymentResponse> getAllPayments(Pageable pageable);

    /* --- lookups --- */
    List<PaymentResponse> getPaymentsByTenant(String tenantId);
    List<PaymentResponse> getPaymentsByOwner(String ownerId);
    List<PaymentResponse> getOverduePayments();

    /* --- pay --- */
    InitiatePaymentResponse initiatePayment(InitiatePaymentRequest dto);
    PaymentResponse verifyPayment(VerifyPaymentRequest dto);
    PaymentResponse payCash(String paymentId, PayCashRequest body);

    /* --- documents --- */
    InvoiceResponse getInvoice(String paymentId);
    ReceiptResponse getReceipt(String paymentId);
    byte[] getInvoicePdf(String paymentId);
    byte[] getReceiptPdf(String paymentId);

    /* --- analytics --- */
    PaymentStatsResponse getStatsByTenant(String tenantId);
    PaymentStatsResponse getStatsByOwner(String ownerId);

    /* --- cross-service consumers --- */
    void onFlatOccupied(String flatId, String tenantId, BigDecimal rentAmount, LocalDate leaseStartDate);
    void onFlatVacated(String flatId, String tenantId);

    /**
     * Idempotent webhook → mark-paid path. Used by
     * {@link com.spa.home_rental_application.payment_service.payment_service.controller.PaymentGatewayController#webhook}.
     *
     * <p>The {@code gatewayName}+{@code eventKey} tuple uniquely
     * identifies an inbound webhook event. We persist it before
     * touching the payment row so retries (Razorpay retries failed
     * deliveries up to 24h, with exponential backoff) collide on the
     * unique constraint and exit early without double-crediting.
     *
     * @return {@code WebhookOutcome.PROCESSED} if this is the first
     *         time we've seen the event AND the payment was flipped to
     *         PAID; {@code DUPLICATE} if we've already processed it;
     *         {@code IGNORED} if there's no resolvable payment or the
     *         payment is already PAID.
     */
    WebhookOutcome markPaidByWebhook(String gatewayName,
                                     String eventKey,
                                     String paymentId,
                                     String transactionId);

    /** Outcome of a webhook → mark-paid attempt. */
    enum WebhookOutcome { PROCESSED, DUPLICATE, IGNORED }
}
