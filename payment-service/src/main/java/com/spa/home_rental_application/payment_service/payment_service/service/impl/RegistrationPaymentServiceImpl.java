package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.CreateRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiateRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.CreateRegistrationPaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InitiatePaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.RegistrationPaymentResultResponse;
import com.spa.home_rental_application.payment_service.payment_service.client.AuthServiceFeign;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentNotFoundException;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import com.spa.home_rental_application.payment_service.payment_service.service.RegistrationPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the paid maintainer-signup payment flow. Sits
 * alongside {@code PaymentServiceImpl} rather than inside it because
 * the lifecycle is different enough (no flat, no owner, no late-fee
 * policy) that intermingling them would obscure both. Hot-path calls
 * still delegate to {@code PaymentServiceImpl} for the Razorpay
 * initiate / verify / mark-paid + Kafka event, so the registration
 * flow inherits every behaviour the existing rent flow already has
 * (concurrency lock, HMAC check, receipt PDF generation, etc.).
 */
@Service
@Slf4j
public class RegistrationPaymentServiceImpl implements RegistrationPaymentService {

    /**
     * Sentinel value stuffed into {@code Payment.flatId} for a
     * registration fee — the table requires a non-null value (legacy
     * @Column constraint), but the row isn't actually tied to any flat.
     * Lookups by flat_id never resolve this sentinel by design, so it
     * doesn't appear in any tenant's payments list.
     */
    private static final String SENTINEL_FLAT_ID = "REGISTRATION_FEE";

    private final PaymentRepository paymentRepo;
    private final PaymentService paymentService;
    private final AuthServiceFeign authServiceFeign;

    public RegistrationPaymentServiceImpl(PaymentRepository paymentRepo,
                                           PaymentService paymentService,
                                           AuthServiceFeign authServiceFeign) {
        this.paymentRepo = paymentRepo;
        this.paymentService = paymentService;
        this.authServiceFeign = authServiceFeign;
    }

    @Override
    @Transactional
    public CreateRegistrationPaymentResponse createPending(CreateRegistrationPaymentRequest req) {
        log.info("RegistrationPayment createPending payerAuthUserId={} amountInr={}",
                req.payerAuthUserId(), req.amountInr());

        // Idempotency: if the same user already has a live PENDING
        // registration payment, hand back that id instead of cluttering
        // the table with duplicates. A user who refreshed the paywall
        // mid-flow hits this path and resumes the same Payment.
        List<Payment> existing = paymentRepo.findPendingRegistrationPaymentsForUser(req.payerAuthUserId());
        if (!existing.isEmpty()) {
            Payment reused = existing.get(0);
            log.info("Reusing existing PENDING registration payment {} for user {}",
                    reused.getId(), req.payerAuthUserId());
            return new CreateRegistrationPaymentResponse(reused.getId());
        }

        BigDecimal amount = req.amountInr();
        Payment p = Payment.builder()
                .tenantId(req.payerAuthUserId())
                .flatId(SENTINEL_FLAT_ID)
                .ownerId(null) // platform receives the fee, not a building owner
                .amount(amount)
                .lateFee(BigDecimal.ZERO)
                .totalAmount(amount)
                .dueDate(LocalDate.now())
                .status(PaymentStatus.PENDING)
                .sourceType("MAINTAINER_REGISTRATION")
                .build();
        Payment saved = paymentRepo.save(p);
        log.info("Created PENDING registration payment {} for user {}", saved.getId(), req.payerAuthUserId());
        return new CreateRegistrationPaymentResponse(saved.getId());
    }

    @Override
    public InitiatePaymentResponse initiate(InitiateRegistrationPaymentRequest req) {
        Payment p = paymentRepo.findById(req.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Registration payment not found: " + req.paymentId()));
        if (!"MAINTAINER_REGISTRATION".equals(p.getSourceType())) {
            throw new IllegalStateException(
                    "Payment " + req.paymentId() + " is not a maintainer-registration fee.");
        }
        // Delegate to the standard initiate path — same Razorpay gateway,
        // same concurrency guards, same status flip to PROCESSING.
        // bankReference + returnUrl are unused by the registration flow
        // (we open Razorpay's Checkout.js modal client-side rather than
        // a hosted-page redirect), so we pass nulls.
        InitiatePaymentRequest delegated = new InitiatePaymentRequest(
                req.paymentId(),
                req.paymentMethod(),
                null, // upiApp
                req.upiVpa(),
                null, // walletProvider
                null, // cardNetwork
                null, // cardLast4
                null, // bankReference
                null  // returnUrl
        );
        return paymentService.initiatePayment(delegated);
    }

    @Override
    public RegistrationPaymentResultResponse verify(VerifyRegistrationPaymentRequest req, Long authUserIdFromToken) {
        Payment p = paymentRepo.findById(req.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Registration payment not found: " + req.paymentId()));
        if (!"MAINTAINER_REGISTRATION".equals(p.getSourceType())) {
            throw new IllegalStateException(
                    "Payment " + req.paymentId() + " is not a maintainer-registration fee.");
        }
        // Belt-and-braces: the JWT verifier already checked that the
        // token's paymentId claim matches the body. Cross-check that
        // the token's uid claim matches the Payment's payer to defend
        // against a tampered or recycled token.
        if (!String.valueOf(authUserIdFromToken).equals(p.getTenantId())) {
            log.warn("Registration payment verify: token uid={} != payment.tenantId={}",
                    authUserIdFromToken, p.getTenantId());
            throw new IllegalStateException(
                    "This token does not belong to the supplied paymentId.");
        }

        // Standard /verify path — HMAC check, mark PAID, Kafka event,
        // receipt. If the gateway rejects, this throws and we never
        // reach the activation Feign call.
        PaymentResponse verifiedResp = paymentService.verifyPayment(new VerifyPaymentRequest(
                req.paymentId(),
                req.razorpayOrderId(),         // gatewayOrderId
                req.razorpayPaymentId(),       // transactionId on the Payment
                req.razorpaySignature()
        ));

        // Defensive: if anything else mutated the row between markPaid
        // and the activation Feign, we still want to attempt activation
        // — better to retry idempotently than to skip it.
        boolean activated = false;
        try {
            Map<String, Object> resp = authServiceFeign.activateRegistration(
                    authUserIdFromToken,
                    Map.of("paymentId", req.paymentId()));
            activated = resp != null;
            log.info("Registration activated authUserId={} paymentId={}",
                    authUserIdFromToken, req.paymentId());
        } catch (Exception ex) {
            // Money already moved at Razorpay + status already PAID
            // locally. The reconciler will retry — DO NOT rethrow.
            log.warn("activateRegistration Feign failed for authUserId={} paymentId={}: {} — "
                            + "deferring to RegistrationActivationReconciler",
                    authUserIdFromToken, req.paymentId(), ex.getMessage());
        }

        return new RegistrationPaymentResultResponse(
                verifiedResp.id(),
                verifiedResp.status() != null ? verifiedResp.status().name() : "PAID",
                activated);
    }
}
