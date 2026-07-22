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

    /**
     * Outstanding-dues summary for a single flat — PENDING + OVERDUE
     * invoices only. Used by property-service when validating a
     * tenant-initiated vacate request (Issue #5). Returns an empty
     * summary (count=0, total=0) when the flat is fully paid up.
     */
    UnpaidSummaryDTO getUnpaidByFlat(String flatId);

    /**
     * Audit L4: paginated overdue listing. At catalog scale (10k+
     * overdue rows during a payment-cycle blip) the unpaginated
     * variant 504s; this one streams a page at a time.
     */
    Page<PaymentResponse> getOverduePayments(Pageable pageable);

    /* --- pay --- */
    InitiatePaymentResponse initiatePayment(InitiatePaymentRequest dto);
    PaymentResponse verifyPayment(VerifyPaymentRequest dto);
    PaymentResponse payCash(String paymentId, PayCashRequest body);

    /**
     * Owner confirms an out-of-band UPI / NEFT / IMPS transfer.
     * Same shape as {@link #payCash} — owner is the actor, the
     * money never touched the platform. Distinct entry point so the
     * receipt PDF + audit trail can carry method=UPI and a UPI
     * reference number instead of a cash receipt id.
     */
    PaymentResponse markUpiReceived(String paymentId, PayCashRequest body);

    /**
     * Tenant self-reports that they've completed a direct-UPI payment
     * out-of-band. Marks the Payment PAID with a TENANT-REPORTED
     * marker so the owner sees it as paid immediately in their
     * dashboard while still being able to tell the reported-vs-
     * verified apart by the reference / audit trail.
     *
     * <p>This is the "trust-based" path we use post-Razorpay-off:
     * direct UPI has no server-side confirmation, so the tenant is
     * the source of truth for "I paid". Owner can reverse via the
     * existing owner-side vacate/reset flow if the deposit never
     * shows up in their bank.
     *
     * <p>Authz: caller must be the tenant of this payment. Owner
     * uses {@link #markUpiReceived} instead.
     */
    PaymentResponse tenantReportPaid(String paymentId, String note);

    /**
     * Owner (or admin) reverts a wrongly-marked-PAID payment back to
     * DUE. Used when a tenant self-reported via
     * {@link #tenantReportPaid} but the money never actually landed
     * in the owner's bank, or when the owner clicked "Mark paid" on
     * the wrong row by mistake.
     *
     * <p>Clears {@code paymentDate}, {@code transactionId},
     * {@code gatewayName}, {@code gatewayOrderId} so a subsequent
     * pay attempt gets a clean slate. The Receipt row for the
     * cleared payment stays in the DB as a historical breadcrumb
     * (never overwritten) — new payment attempts generate a fresh
     * receipt on completion.
     *
     * <p>Audit event {@code payment.reverted-to-due} captures actor
     * (owner), subject (tenant), amount, previous state + reason.
     * No Kafka {@code payment.reverted} event today — downstream
     * flows (property-service society-collection sync, etc.) don't
     * currently need a reversal signal, and adding one is easy when
     * they do.
     */
    PaymentResponse revertPaymentToDue(String paymentId, String reason);

    /**
     * Resolve everything the tenant needs to pay rent directly to
     * the owner's UPI VPA or bank account. Built from the owner's
     * saved bank-account row (via Feign to user-service); returns
     * {@code ownerPayoutMissing=true} when the owner hasn't saved
     * any payment details yet.
     */
    com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PayoutDetailsResponse
            getPayoutDetails(String paymentId);

    /* --- documents --- */
    InvoiceResponse getInvoice(String paymentId);
    ReceiptResponse getReceipt(String paymentId);
    byte[] getInvoicePdf(String paymentId);
    byte[] getReceiptPdf(String paymentId);

    /* --- analytics --- */
    PaymentStatsResponse getStatsByTenant(String tenantId);
    PaymentStatsResponse getStatsByOwner(String ownerId);

    /**
     * Public landing-page stat — the lifetime sum of all PAID rent
     * payments across the platform. Used by the live-stats strip on
     * {@code /} and {@code /about} to surface "₹X processed" social
     * proof. No PII: returns just the aggregate rupee amount, never
     * per-payment / per-tenant detail.
     *
     * <p>Zero when no payments have settled yet — caller (landing) hides
     * the tile rather than displaying ₹0.
     */
    BigDecimal getLifetimeCollectedRupees();

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
