package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.DTO.PaymentMapper;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.*;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.*;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Invoice;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
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
    private final PaymentGateway gateway;
    private final PaymentServiceEvents events;
    private final PaymentProperties props;

    public PaymentServiceImpl(PaymentRepository paymentRepo,
                              InvoiceRepository invoiceRepo,
                              ReceiptRepository receiptRepo,
                              PaymentGateway gateway,
                              PaymentServiceEvents events,
                              PaymentProperties props) {
        this.paymentRepo = paymentRepo;
        this.invoiceRepo = invoiceRepo;
        this.receiptRepo = receiptRepo;
        this.gateway = gateway;
        this.events = events;
        this.props = props;
    }

    /* ---------------- Lifecycle ---------------- */

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest dto) {
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

        return PaymentMapper.toResponse(saved);
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
    public List<PaymentResponse> getPaymentsByOwner(String ownerId) {
        return paymentRepo.findByOwnerId(ownerId).stream().map(PaymentMapper::toResponse).toList();
    }

    @Override
    public List<PaymentResponse> getOverduePayments() {
        return paymentRepo.findByStatus(PaymentStatus.OVERDUE).stream().map(PaymentMapper::toResponse).toList();
    }

    /* ---------------- Pay ---------------- */

    @Override
    @Transactional
    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest dto) {
        Payment p = mustFind(dto.paymentId());
        guardNotPaid(p);

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
        return PaymentMapper.toResponse(p);
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
        Payment p = Payment.builder()
                .tenantId(tenantId).flatId(flatId)
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
