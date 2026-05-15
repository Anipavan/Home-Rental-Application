package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient;
import com.spa.home_rental_application.payment_service.payment_service.client.UserClient;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PayoutDetailsResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.PaymentMapper;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.*;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.*;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Invoice;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.entities.ProcessedWebhook;
import com.spa.home_rental_application.payment_service.payment_service.entities.Receipt;
import com.spa.home_rental_application.payment_service.payment_service.enums.*;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentAlreadyPaidException;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentGatewayException;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentNotFoundException;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentInitiationResult;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentVerificationResult;
import com.spa.home_rental_application.payment_service.payment_service.repository.InvoiceRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.ProcessedWebhookRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.ReceiptRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final Set<PaymentStatus> ACTIVE = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.OVERDUE);

    private final PaymentRepository paymentRepo;
    private final InvoiceRepository invoiceRepo;
    private final ReceiptRepository receiptRepo;
    private final ProcessedWebhookRepository webhookRepo;
    private final PaymentGateway gateway;
    private final PaymentServiceEvents events;
    private final PaymentProperties props;
    private final PaymentPdfGenerator pdfGenerator;
    private final PropertyClient propertyClient;
    private final UserClient userClient;
    private final AuditEventPublisher audit;

    public PaymentServiceImpl(PaymentRepository paymentRepo,
                              InvoiceRepository invoiceRepo,
                              ReceiptRepository receiptRepo,
                              ProcessedWebhookRepository webhookRepo,
                              PaymentGateway gateway,
                              PaymentServiceEvents events,
                              PaymentProperties props,
                              PaymentPdfGenerator pdfGenerator,
                              PropertyClient propertyClient,
                              UserClient userClient,
                              AuditEventPublisher audit) {
        this.paymentRepo = paymentRepo;
        this.invoiceRepo = invoiceRepo;
        this.receiptRepo = receiptRepo;
        this.webhookRepo = webhookRepo;
        this.gateway = gateway;
        this.events = events;
        this.props = props;
        this.pdfGenerator = pdfGenerator;
        this.propertyClient = propertyClient;
        this.userClient = userClient;
        this.audit = audit;
    }

    /* ---------------- Lifecycle ---------------- */

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest dto) {
        return createPayment(dto, null);
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest dto, String idempotencyKey) {
        // Audit M13: idempotency-key fast path. The ProcessedWebhook
        // table is reused as a generic "have we already handled this
        // request" log keyed on (gatewayName=IDEMPOTENCY, eventKey).
        // A retry with the same key returns the payment recorded
        // against that key; brand-new keys flow through to the
        // standard create path + record the key.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var prior = webhookRepo.findByGatewayNameAndEventKey("IDEMPOTENCY", idempotencyKey);
            if (prior.isPresent() && prior.get().getPaymentId() != null) {
                Payment recorded = paymentRepo.findById(prior.get().getPaymentId()).orElse(null);
                if (recorded != null) {
                    log.info("Idempotent re-create matched key={} → returning existing paymentId={}",
                            idempotencyKey, recorded.getId());
                    return PaymentMapper.toResponse(recorded);
                }
            }
        }
        Payment created = doCreatePayment(dto);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                webhookRepo.save(ProcessedWebhook.builder()
                        .gatewayName("IDEMPOTENCY")
                        .eventKey(idempotencyKey)
                        .paymentId(created.getId())
                        .outcome("PROCESSED")
                        .processedAt(Instant.now())
                        .build());
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                // Concurrent identical request — the other transaction
                // won the insert. We've already created OUR payment;
                // return it. The dedupe row in the other transaction
                // points at the other payment, but the next retry
                // from the SAME caller will see the dedupe row and
                // return whichever payment is recorded. Edge case
                // only matters with truly-parallel identical requests
                // — extremely rare in practice.
                log.info("Idempotency key collision (concurrent identical request): {}", idempotencyKey);
            }
        }
        return PaymentMapper.toResponse(created);
    }

    /** Extracted core insert path so both createPayment overloads share it. */
    private Payment doCreatePayment(CreatePaymentRequest dto) {
        // Audit H21: service-level guard on amount. The DTO already
        // has @Positive, but defence-in-depth — every code path that
        // reaches createPayment (REST, internal Feign, scheduled rent
        // cycles) must produce a row with amount > 0.
        if (dto.amount() == null || dto.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be strictly positive. Received: " + dto.amount());
        }
        // Audit H23: refuse to persist a payment with no dueDate.
        if (dto.dueDate() == null) {
            throw new IllegalArgumentException("Payment dueDate is required.");
        }

        Payment p = Payment.builder()
                .tenantId(dto.tenantId())
                .flatId(dto.flatId())
                .ownerId(dto.ownerId())
                .amount(dto.amount())
                .lateFee(BigDecimal.ZERO)
                .totalAmount(dto.amount())
                .dueDate(dto.dueDate())
                .status(PaymentStatus.PENDING)
                .build();
        Payment saved = paymentRepo.save(p);
        Invoice inv = generateInvoice(saved);

        events.sendPaymentCreated(PaymentCreatedEvent.builder()
                .eventType("payment.created")
                .paymentId(saved.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .tenantId(saved.getTenantId())
                .flatId(saved.getFlatId())
                .ownerId(saved.getOwnerId())
                .amount(saved.getTotalAmount())
                .dueDate(saved.getDueDate())
                .timestamp(Instant.now())
                .build());

        return saved;
    }

    @Override
    public PaymentResponse getPaymentById(String id) {
        return PaymentMapper.toResponse(mustFind(id));
    }

    @Override
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        return paymentRepo.findAll(pageable).map(PaymentMapper::toResponse);
    }

    /* ---------------- Lookups ---------------- */

    @Override
    public List<PaymentResponse> getPaymentsByTenant(String tenantId) {
        return paymentRepo.findByTenantId(tenantId).stream().map(PaymentMapper::toResponse).toList();
    }

    @Override
    @Transactional   // Back-fill saveAll(...) on legacy rows needs an active tx.
    public List<PaymentResponse> getPaymentsByOwner(String ownerId) {
        // Two-pass lookup that heals legacy data on the way through.
        //
        // Pass 1: rows explicitly tagged with this owner (the happy path
        // for new payments created after the onFlatOccupied fix).
        //
        // Pass 2: rows with ownerId=null whose flatId belongs to one
        // of this owner's buildings. We resolve the owner's flatIds
        // via the property-service Feign call, query payments by
        // flatId-in, and back-fill the ownerId column on every row
        // we find so the next call to this method serves them from
        // pass 1 (no Feign round-trip needed). This is what makes
        // /payments/owner/{id} actually return the right rows for
        // the tenants page + tenant-detail Payments section after
        // earlier billing runs that landed ownerId=null.
        List<Payment> tagged = paymentRepo.findByOwnerId(ownerId);
        Map<String, Payment> merged = new LinkedHashMap<>();
        for (Payment p : tagged) merged.put(p.getId(), p);

        Set<String> ownerFlatIds = collectOwnerFlatIds(ownerId);
        if (!ownerFlatIds.isEmpty()) {
            List<Payment> byFlat = paymentRepo.findByFlatIdIn(ownerFlatIds);
            List<Payment> toHeal = new ArrayList<>();
            for (Payment p : byFlat) {
                merged.putIfAbsent(p.getId(), p);
                if (p.getOwnerId() == null || p.getOwnerId().isBlank()) {
                    p.setOwnerId(ownerId);
                    toHeal.add(p);
                }
            }
            if (!toHeal.isEmpty()) {
                paymentRepo.saveAll(toHeal);
                log.info("Back-filled ownerId on {} legacy payment rows for owner={}",
                        toHeal.size(), ownerId);
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(Payment::getDueDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(PaymentMapper::toResponse)
                .toList();
    }

    /**
     * Resolve the owner of a flat through property-service. Returns
     * null on any failure (offline, 404, etc.) so callers can treat
     * the lookup as best-effort. Pulled into a helper so both
     * {@code onFlatOccupied} (write side) and the back-fill path use
     * the same flat→building→ownerId resolution.
     */
    private String resolveOwnerIdForFlat(String flatId) {
        if (flatId == null || flatId.isBlank()) return null;
        try {
            PropertyClient.FlatSummary flat = propertyClient.getFlatById(flatId);
            if (flat == null || flat.buildingId() == null) return null;
            PropertyClient.BuildingSummary building = propertyClient.getBuildingById(flat.buildingId());
            return building == null ? null : building.ownerId();
        } catch (Exception ex) {
            log.warn("Failed to resolve ownerId for flatId={} via property-service: {}",
                    flatId, ex.getMessage());
            return null;
        }
    }

    /**
     * Resolve every flat the owner has on file. Used by the
     * legacy-row back-fill inside {@link #getPaymentsByOwner}. Returns
     * an empty set when property-service is unreachable so the back-fill
     * path no-ops gracefully (pass 1 still runs).
     */
    private Set<String> collectOwnerFlatIds(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return Set.of();
        try {
            List<PropertyClient.BuildingSummary> buildings = propertyClient.getBuildingsByOwner(ownerId);
            if (buildings == null || buildings.isEmpty()) return Set.of();
            Set<String> flatIds = new LinkedHashSet<>();
            for (PropertyClient.BuildingSummary b : buildings) {
                if (b == null || b.buildingId() == null) continue;
                List<PropertyClient.FlatSummary> flats = propertyClient.getFlatsByBuilding(b.buildingId());
                if (flats == null) continue;
                for (PropertyClient.FlatSummary f : flats) {
                    if (f != null && f.id() != null) flatIds.add(f.id());
                }
            }
            return flatIds;
        } catch (Exception ex) {
            log.warn("Failed to collect owner's flat ids for ownerId={}: {}",
                    ownerId, ex.getMessage());
            return Set.of();
        }
    }

    @Override
    public List<PaymentResponse> getOverduePayments() {
        return paymentRepo.findByStatus(PaymentStatus.OVERDUE).stream().map(PaymentMapper::toResponse).toList();
    }

    @Override
    public Page<PaymentResponse> getOverduePayments(Pageable pageable) {
        // Audit L4 — paginated overdue listing for the collections-ops
        // dashboard. Oldest-overdue first mirrors the natural triage
        // order ops actually works through.
        return paymentRepo.findByStatusOrderByDueDateAsc(PaymentStatus.OVERDUE, pageable)
                .map(PaymentMapper::toResponse);
    }

    @Override
    public UnpaidSummaryDTO getUnpaidByFlat(String flatId) {
        // PENDING + OVERDUE invoices block a tenant-initiated vacate per
        // Issue #5. PROCESSING is in-flight (gateway hasn't confirmed
        // success/failure yet) — we EXCLUDE it from "outstanding" so a
        // tenant who just clicked Pay isn't blocked by a 60-second
        // settlement window.
        List<Payment> unpaid = paymentRepo.findByFlatIdAndStatusIn(
                flatId, List.of(PaymentStatus.PENDING, PaymentStatus.OVERDUE));
        if (unpaid.isEmpty()) return UnpaidSummaryDTO.empty(flatId);
        BigDecimal total = unpaid.stream()
                .map(p -> p.getTotalAmount() != null
                        ? p.getTotalAmount()
                        : (p.getAmount() == null ? BigDecimal.ZERO : p.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Payment has no separate "invoice number" column — the payment
        // id itself is the user-facing identifier in this codebase
        // (it's what the tenant sees on /app/payments). Returning the
        // ids gives the caller a stable handle for each unpaid row
        // without inventing an artificial invoice numbering scheme.
        List<String> invoiceNumbers = unpaid.stream()
                .map(Payment::getId)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        return new UnpaidSummaryDTO(flatId, unpaid.size(), total, invoiceNumbers);
    }

    /* ---------------- Pay ---------------- */

    @Override
    @Transactional
    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest dto) {
        Payment p = mustFind(dto.paymentId());
        guardNotPaid(p);

        // Audit H22: refuse re-initiation if another concurrent thread
        // already moved the payment past PENDING/OVERDUE. Without this
        // guard two simultaneous "Pay" clicks could each generate a
        // gateway order and the tenant would be double-charged. The
        // status check inside the @Transactional block + SELECT FOR
        // UPDATE-style re-load makes the transition atomic at the
        // application layer (Oracle SERIALIZABLE-equivalent under the
        // default isolation).
        PaymentStatus current = p.getStatus();
        if (current != null && current != PaymentStatus.PENDING && current != PaymentStatus.OVERDUE) {
            throw new IllegalStateException(
                    "Payment " + p.getId() + " cannot be initiated from status " + current
                            + ". Refresh the page — another tab may already be processing it.");
        }

        // Snapshot the chosen method onto the entity so the receipt + analytics
        // know how the tenant paid.
        p.setPaymentMethod(dto.paymentMethod());
        p.setUpiApp(dto.upiApp());
        p.setUpiVpa(dto.upiVpa());
        p.setWalletProvider(dto.walletProvider());
        p.setCardNetwork(dto.cardNetwork());
        p.setCardLast4(dto.cardLast4());
        p.setStatus(PaymentStatus.PROCESSING);

        PaymentInitiationResult r = gateway.initiate(p, dto);
        p.setGatewayName(r.getGatewayName());
        p.setGatewayOrderId(r.getGatewayOrderId());
        paymentRepo.save(p);

        return new InitiatePaymentResponse(
                p.getId(), p.getPaymentMethod(),
                r.getGatewayName(), r.getGatewayOrderId(),
                p.getTotalAmount(), "INR",
                r.getRedirectUrl(),
                r.getUpiIntentUrl(),
                r.getUpiCollectStatus(),
                r.getBankAccountNumber(), r.getBankIfsc(), r.getBankAccountName());
    }

    @Override
    @Transactional
    public PaymentResponse verifyPayment(VerifyPaymentRequest dto) {
        Payment p = mustFind(dto.paymentId());
        guardNotPaid(p);

        PaymentVerificationResult r = gateway.verify(p, dto);
        if (!r.isSuccess()) {
            p.setStatus(PaymentStatus.FAILED);
            p.setFailureReason(r.getFailureReason());
            paymentRepo.save(p);

            events.sendPaymentFailed(PaymentFailedEvent.builder()
                    .eventType("payment.failed")
                    .paymentId(p.getId())
                    .tenantId(p.getTenantId())
                    .reason(r.getFailureReason())
                    .gatewayErrorCode(r.getGatewayErrorCode())
                    .timestamp(Instant.now())
                    .build());

            throw new PaymentGatewayException("Payment verification failed: " + r.getFailureReason(),
                    r.getGatewayErrorCode());
        }

        markPaid(p, r.getTransactionId());
        return PaymentMapper.toResponse(p);
    }

    @Override
    @Transactional
    public PaymentResponse payCash(String paymentId, PayCashRequest body) {
        Payment p = mustFind(paymentId);
        guardNotPaid(p);

        p.setPaymentMethod(PaymentMethod.CASH);
        p.setGatewayName("manual");
        p.setGatewayOrderId(null);
        markPaid(p, body.reference() != null ? body.reference() : "CASH-" + UUID.randomUUID());

        // P1-12: cash settlement is a high-signal event for audit —
        // it's the path where money changes hands outside any
        // payment gateway. Capture actor (owner), subject (tenant),
        // amount + reference for the security operations index.
        audit.publishSuccess("payment.cash.recorded",
                body.ownerId(), p.getTenantId(), p.getId(),
                java.util.Map.of(
                        "amount", String.valueOf(p.getTotalAmount()),
                        "flatId", String.valueOf(p.getFlatId()),
                        "reference", body.reference() == null ? "" : body.reference()));

        return PaymentMapper.toResponse(p);
    }

    /**
     * Tenant-pays-rent-direct-to-owner flow. The tenant has already
     * paid the owner out-of-band (UPI scan, NEFT, IMPS) — this
     * endpoint is the OWNER coming back to confirm receipt. From
     * the platform's POV it's the same shape as
     * {@link #payCash(String, com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest)}
     * — only the {@code PaymentMethod} differs. We keep them as
     * separate entry points so the audit trail + receipt PDF can
     * distinguish "owner saw money in their UPI app" from "owner
     * accepted physical cash".
     */
    @Override
    @Transactional
    public PaymentResponse markUpiReceived(String paymentId,
                                           com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest body) {
        Payment p = mustFind(paymentId);
        guardNotPaid(p);

        p.setPaymentMethod(PaymentMethod.UPI);
        p.setGatewayName("manual-upi");
        p.setGatewayOrderId(null);
        // Reference is typically the UPI reference number ("UPI Ref:
        // 412395123456") which the owner copies out of their bank
        // SMS / app. Falls back to an auto-generated id when blank.
        markPaid(p, body.reference() != null && !body.reference().isBlank()
                ? body.reference()
                : "UPI-" + UUID.randomUUID());

        audit.publishSuccess("payment.upi.received",
                body.ownerId(), p.getTenantId(), p.getId(),
                java.util.Map.of(
                        "amount", String.valueOf(p.getTotalAmount()),
                        "flatId", String.valueOf(p.getFlatId()),
                        "reference", body.reference() == null ? "" : body.reference()));

        return PaymentMapper.toResponse(p);
    }

    /**
     * Assemble everything the tenant needs to pay rent directly to
     * the owner — UPI VPA + QR deep link as the preferred path,
     * masked bank account + IFSC as the NEFT/IMPS fallback. See
     * {@link PayoutDetailsResponse} for the contract.
     *
     * <p>Calls into user-service (UserClient Feign) to fetch the
     * owner's saved bank-account row. Feign failures are absorbed
     * by the fallback into an empty PayoutDetails — surfaces as
     * {@code ownerPayoutMissing=true} so the FE can render a
     * "couldn't load payment details" message rather than a 500.
     */
    @Override
    @Transactional(readOnly = true)
    public PayoutDetailsResponse getPayoutDetails(String paymentId) {
        Payment p = mustFind(paymentId);

        // Resolve the owner. New payments stamp ownerId at write
        // time (P0/Issue-fix from earlier); legacy null-ownerId rows
        // get back-filled the next time the owner views their
        // payments list, but the tenant might race that. Fail-soft
        // here: if ownerId is null, surface as payout-missing so
        // the tenant gets a graceful message.
        String ownerId = p.getOwnerId();
        if (ownerId == null || ownerId.isBlank()) {
            log.warn("Payment {} has no ownerId — payout-details returning empty",
                    paymentId);
            return new PayoutDetailsResponse(
                    p.getId(), p.getTotalAmount(), p.getId(),
                    null, null,
                    null, null,
                    null, null, null, null, null,
                    true);
        }

        UserClient.PayoutDetails payout;
        try {
            payout = userClient.getPayoutDetails(ownerId);
        } catch (Exception ex) {
            log.warn("Owner payout lookup failed for paymentId={} ownerId={}: {}",
                    paymentId, ownerId, ex.getMessage());
            payout = UserClient.PayoutDetails.empty();
        }
        boolean missing = payout == null
                || (isBlank(payout.upiId())
                    && isBlank(payout.accountNumberMasked())
                    && isBlank(payout.ifscCode()));

        String upiPayload = null;
        if (payout != null && !isBlank(payout.upiId())) {
            upiPayload = buildUpiDeepLink(
                    payout.upiId(),
                    payout.accountHolderName(),
                    p.getTotalAmount(),
                    "Rent for flat " + p.getFlatId());
        }

        return new PayoutDetailsResponse(
                p.getId(),
                p.getTotalAmount(),
                p.getId(),
                payout == null ? null : payout.accountHolderName(),
                ownerId,
                payout == null ? null : payout.upiId(),
                upiPayload,
                payout == null ? null : payout.bankName(),
                payout == null ? null : payout.accountNumberMasked(),
                payout == null ? null : payout.ifscCode(),
                payout == null ? null : payout.branch(),
                payout == null ? null : payout.accountType(),
                missing);
    }

    /**
     * RFC-style UPI deep link. Most India UPI apps (GPay, PhonePe,
     * Paytm, BHIM, Amazon Pay) recognise this shape from a QR scan
     * and pre-fill the amount + payee. The `tn` (transaction note)
     * shows up in the user's payment history so they can audit
     * later.
     *
     * <p>Amount goes in with 2-decimal precision because some UPI
     * apps reject anything else.
     */
    private static String buildUpiDeepLink(String vpa, String payeeName,
                                            java.math.BigDecimal amount, String note) {
        String pn = urlEncode(payeeName == null ? "Owner" : payeeName);
        String tn = urlEncode(note == null ? "Rent payment" : note);
        String am = amount == null ? "0.00"
                : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return "upi://pay?pa=" + urlEncode(vpa)
                + "&pn=" + pn
                + "&am=" + am
                + "&cu=INR"
                + "&tn=" + tn;
    }

    private static String urlEncode(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Common path for any successful payment (gateway or cash). */
    private void markPaid(Payment p, String transactionId) {
        Instant now = Instant.now();
        p.setStatus(PaymentStatus.PAID);
        p.setTransactionId(transactionId);
        p.setPaymentDate(now);
        paymentRepo.save(p);

        Receipt receipt = generateReceipt(p);

        events.sendPaymentCompleted(PaymentCompletedEvent.builder()
                .eventType("payment.completed")
                .paymentId(p.getId())
                .tenantId(p.getTenantId())
                .ownerId(p.getOwnerId())
                .amount(p.getTotalAmount())
                .paymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null)
                .transactionId(transactionId)
                .paidDate(now)
                .timestamp(now)
                .build());
        log.info("Payment {} completed via {} (receipt {}, txn {})",
                p.getId(), p.getPaymentMethod(), receipt.getReceiptNumber(), transactionId);
    }

    @Override
    @Transactional
    public WebhookOutcome markPaidByWebhook(String gatewayName,
                                            String eventKey,
                                            String paymentId,
                                            String transactionId) {
        // Idempotency fast-path. The unique constraint on
        // (gateway_name, event_key) is the real guarantor — this is
        // just the cheap optimistic read.
        if (gatewayName == null || eventKey == null || eventKey.isBlank()) {
            log.warn("Webhook missing gatewayName/eventKey — refusing to credit; gw={} key={}",
                    gatewayName, eventKey);
            return WebhookOutcome.IGNORED;
        }
        Optional<ProcessedWebhook> existing =
                webhookRepo.findByGatewayNameAndEventKey(gatewayName, eventKey);
        if (existing.isPresent()) {
            log.info("Webhook DUPLICATE: gateway={} eventKey={} previousOutcome={}",
                    gatewayName, eventKey, existing.get().getOutcome());
            return WebhookOutcome.DUPLICATE;
        }

        if (paymentId == null || paymentId.isBlank()) {
            // Couldn't extract a local payment id from the payload —
            // record the event anyway so we don't re-process it, but
            // mark it IGNORED so ops can investigate.
            persistWebhookLog(gatewayName, eventKey, null, transactionId, "IGNORED");
            log.warn("Webhook accepted but no resolvable paymentId — gw={} eventKey={}",
                    gatewayName, eventKey);
            return WebhookOutcome.IGNORED;
        }

        Payment p = paymentRepo.findById(paymentId).orElse(null);
        if (p == null) {
            persistWebhookLog(gatewayName, eventKey, paymentId, transactionId, "IGNORED");
            log.warn("Webhook {} for unknown paymentId={} — ignored", eventKey, paymentId);
            return WebhookOutcome.IGNORED;
        }

        if (p.getStatus() == PaymentStatus.PAID) {
            // Already paid — still record the webhook so retries collide.
            persistWebhookLog(gatewayName, eventKey, paymentId, transactionId, "IGNORED");
            log.info("Webhook {} for already-PAID payment {} — no-op", eventKey, paymentId);
            return WebhookOutcome.IGNORED;
        }

        // Happy path: persist the dedupe row inside the same transaction
        // as the markPaid() so a race resolves cleanly. If two concurrent
        // webhook deliveries reach this point, the second will fail the
        // unique-constraint insert and Spring will roll back; we catch
        // that and report DUPLICATE.
        try {
            persistWebhookLog(gatewayName, eventKey, paymentId, transactionId, "PROCESSED");
            markPaid(p, transactionId == null ? "WEBHOOK-" + eventKey : transactionId);
            return WebhookOutcome.PROCESSED;
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            log.info("Webhook race lost: gateway={} eventKey={} — another delivery won the insert.",
                    gatewayName, eventKey);
            return WebhookOutcome.DUPLICATE;
        }
    }

    private void persistWebhookLog(String gatewayName, String eventKey,
                                   String paymentId, String transactionId, String outcome) {
        webhookRepo.save(ProcessedWebhook.builder()
                .gatewayName(gatewayName)
                .eventKey(eventKey)
                .paymentId(paymentId)
                .transactionId(transactionId)
                .outcome(outcome)
                .processedAt(Instant.now())
                .build());
    }

    /* ---------------- Documents ---------------- */

    @Override
    public InvoiceResponse getInvoice(String paymentId) {
        return PaymentMapper.toResponse(invoiceRepo.findByPaymentId(paymentId).orElseThrow(
                () -> new PaymentNotFoundException("No invoice for paymentId: " + paymentId)));
    }

    @Override
    public ReceiptResponse getReceipt(String paymentId) {
        return PaymentMapper.toResponse(receiptRepo.findByPaymentId(paymentId).orElseThrow(
                () -> new PaymentNotFoundException("No receipt for paymentId: " + paymentId)));
    }

    @Override
    public byte[] getInvoicePdf(String paymentId) {
        Payment p = mustFind(paymentId);
        Invoice inv = invoiceRepo.findByPaymentId(paymentId).orElseThrow(
                () -> new PaymentNotFoundException("No invoice for paymentId: " + paymentId));
        return pdfGenerator.generateInvoice(p, inv);
    }

    @Override
    public byte[] getReceiptPdf(String paymentId) {
        Payment p = mustFind(paymentId);
        Receipt r = receiptRepo.findByPaymentId(paymentId).orElseThrow(
                () -> new PaymentNotFoundException("No receipt for paymentId: " + paymentId));
        return pdfGenerator.generateReceipt(p, r);
    }

    /* ---------------- Analytics ---------------- */

    @Override
    public PaymentStatsResponse getStatsByTenant(String tenantId) {
        return aggregate(paymentRepo.findByTenantId(tenantId));
    }

    @Override
    public PaymentStatsResponse getStatsByOwner(String ownerId) {
        return aggregate(paymentRepo.findByOwnerId(ownerId));
    }

    private PaymentStatsResponse aggregate(List<Payment> payments) {
        long total = payments.size();
        long paid = 0, pending = 0, overdue = 0, failed = 0;
        BigDecimal paidSum = BigDecimal.ZERO, pendingSum = BigDecimal.ZERO, lateFeeSum = BigDecimal.ZERO;
        for (Payment p : payments) {
            switch (p.getStatus()) {
                case PAID    -> { paid++;    paidSum = paidSum.add(p.getTotalAmount()); }
                case PENDING -> { pending++; pendingSum = pendingSum.add(p.getTotalAmount()); }
                case OVERDUE -> { overdue++; pendingSum = pendingSum.add(p.getTotalAmount()); }
                case FAILED  -> failed++;
                default -> { /* CANCELLED / REFUNDED / PROCESSING */ }
            }
            if (p.getLateFee() != null) lateFeeSum = lateFeeSum.add(p.getLateFee());
        }
        return new PaymentStatsResponse(total, paid, pending, overdue, failed,
                paidSum, pendingSum, lateFeeSum);
    }

    /* ---------------- Cross-service consumers ---------------- */

    /**
     * Auto-create the first invoice when a flat is occupied. Idempotent —
     * if an active payment already exists for this flat we do nothing.
     */
    @Override
    @Transactional
    public void onFlatOccupied(String flatId, String tenantId, BigDecimal rentAmount, LocalDate leaseStartDate) {
        if (rentAmount == null || rentAmount.signum() <= 0) {
            log.info("Skipping payment seed for flat {} — rentAmount invalid", flatId);
            return;
        }
        List<Payment> active = paymentRepo.findByFlatIdAndStatusIn(flatId, ACTIVE);
        if (!active.isEmpty()) {
            log.info("Skipping payment seed for flat {} — {} active payment(s) already exist",
                    flatId, active.size());
            return;
        }
        LocalDate due = (leaseStartDate != null ? leaseStartDate : LocalDate.now()).withDayOfMonth(1).plusMonths(1);
        // Resolve ownerId via property-service so the new rent invoice
        // is attributable to the right landlord. Without this, the row
        // lands with ownerId=null and the owner's /payments/owner/{id}
        // call returns zero rows — which is exactly the bug that made
        // the tenant-detail Payments section render Paid/Overdue/Upcoming
        // = 0 for every tenant. The Feign call is best-effort: if
        // property-service is down (fallback returns null fields) we
        // still create the payment; the lazy back-fill in
        // getPaymentsByOwner will heal it later.
        String resolvedOwnerId = resolveOwnerIdForFlat(flatId);
        Payment p = Payment.builder()
                .tenantId(tenantId).flatId(flatId)
                .ownerId(resolvedOwnerId)
                .amount(rentAmount).lateFee(BigDecimal.ZERO).totalAmount(rentAmount)
                .dueDate(due)
                .status(PaymentStatus.PENDING)
                .build();
        Payment saved = paymentRepo.save(p);
        Invoice inv = generateInvoice(saved);
        events.sendPaymentCreated(PaymentCreatedEvent.builder()
                .eventType("payment.created")
                .paymentId(saved.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .tenantId(saved.getTenantId())
                .flatId(saved.getFlatId())
                .amount(saved.getTotalAmount())
                .dueDate(saved.getDueDate())
                .timestamp(Instant.now())
                .build());
        log.info("Auto-seeded first payment for flat={} tenant={} amount={} due={}",
                flatId, tenantId, rentAmount, due);
    }

    /**
     * Cancel every active payment when a flat is vacated. The tenant has
     * moved out, no further rent is owed against this flat.
     */
    @Override
    @Transactional
    public void onFlatVacated(String flatId, String tenantId) {
        List<Payment> active = paymentRepo.findByFlatIdAndStatusIn(flatId, ACTIVE);
        for (Payment p : active) {
            p.setStatus(PaymentStatus.CANCELLED);
            paymentRepo.save(p);
        }
        log.info("Cancelled {} active payment(s) for vacated flat {}", active.size(), flatId);
    }

    /* ---------------- Helpers ---------------- */

    private Payment mustFind(String id) {
        return paymentRepo.findById(id).orElseThrow(
                () -> new PaymentNotFoundException("Payment not found with id: " + id));
    }

    private void guardNotPaid(Payment p) {
        if (p.getStatus() == PaymentStatus.PAID) {
            throw new PaymentAlreadyPaidException("Payment " + p.getId() + " is already PAID");
        }
        if (p.getStatus() == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyPaidException("Payment " + p.getId() + " is CANCELLED — cannot pay");
        }
    }

    private Invoice generateInvoice(Payment p) {
        Invoice inv = Invoice.builder()
                .paymentId(p.getId())
                .invoiceNumber(generateInvoiceNumber())
                .build();
        return invoiceRepo.save(inv);
    }

    private Receipt generateReceipt(Payment p) {
        return receiptRepo.findByPaymentId(p.getId()).orElseGet(() -> receiptRepo.save(
                Receipt.builder()
                        .paymentId(p.getId())
                        .receiptNumber(generateReceiptNumber())
                        .build()));
    }

    private String generateInvoiceNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return "INV-" + date + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String generateReceiptNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return "RCT-" + date + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /** Compute a late fee given how many days overdue, capped at the configured max. */
    public BigDecimal computeLateFee(BigDecimal baseAmount, long daysOverdue) {
        if (daysOverdue <= 0) return BigDecimal.ZERO;
        long weeks = (daysOverdue + 6) / 7;  // ceiling
        BigDecimal pct = props.getLateFeePercentPerWeek().multiply(BigDecimal.valueOf(weeks));
        BigDecimal capped = pct.min(props.getMaxLateFeePercent());
        return baseAmount.multiply(capped).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
