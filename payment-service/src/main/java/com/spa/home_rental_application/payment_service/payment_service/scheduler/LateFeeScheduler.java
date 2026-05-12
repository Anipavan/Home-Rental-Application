package com.spa.home_rental_application.payment_service.payment_service.scheduler;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentOverdueEvent;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily late-fee + overdue-status maintenance job.
 *
 * <p>Promotes {@link PaymentStatus#PENDING} invoices past their due
 * date to {@link PaymentStatus#OVERDUE} and grows the
 * {@code lateFee} field per the configured rule:
 *
 * <pre>
 *   lateFee = amount * rate% per week, capped at maxPercent of amount
 * </pre>
 *
 * <p>Defaults are conservative for the Indian rental market:
 *   - 2% per week of the rent amount
 *   - capped at 20% of the rent amount
 *
 * <p>Owners' #1 ask is automated late-fee handling — no more chasing
 * tenants for the calculator math. Each promotion also fires a
 * {@code payment.overdue} Kafka event so notification-service can
 * send a fan-out reminder across email / SMS / WhatsApp.
 *
 * <p>The job runs daily at 06:00 IST. Idempotent: re-running on the
 * same day with the same rate produces the same lateFee number.
 */
@Component
@Slf4j
public class LateFeeScheduler {

    private final PaymentRepository repo;
    private final OutboxPublisher outbox;

    /**
     * Late-fee rate per week, expressed as a percentage of the rent
     * amount (e.g. {@code 2.0} = 2% per week). Overridable per-env
     * via {@code app.payment.late-fee.weekly-percent}.
     */
    @Value("${app.payment.late-fee.weekly-percent:2.0}")
    private double weeklyPercent;

    /**
     * Hard ceiling on the late fee as a percent of the rent amount.
     * Without this an overdue payment grows without bound. Default
     * 20% prevents abuse + matches India's Consumer Protection Act
     * guidance on punitive fees.
     */
    @Value("${app.payment.late-fee.max-percent:20.0}")
    private double maxPercent;

    public LateFeeScheduler(PaymentRepository repo, OutboxPublisher outbox) {
        this.repo = repo;
        this.outbox = outbox;
    }

    /**
     * Daily at 06:00 server time. Cron in IST-friendly schedule;
     * adjust via {@code app.payment.late-fee.cron} for other regions.
     */
    @Scheduled(cron = "${app.payment.late-fee.cron:0 0 6 * * *}")
    @Transactional
    public void promoteOverdueAndApplyFees() {
        LocalDate today = LocalDate.now();
        // Two passes — PENDING rows past due date (promote + first
        // fee) and existing OVERDUE rows (grow fee weekly until cap).
        promotePending(today);
        growExistingOverdue(today);
    }

    /**
     * PENDING + due_date < today → promote to OVERDUE, apply first
     * week's fee, fire Kafka event for notification fan-out.
     */
    private void promotePending(LocalDate today) {
        List<Payment> candidates = repo.findOverdueCandidates(PaymentStatus.PENDING, today);
        if (candidates.isEmpty()) return;
        log.info("LateFeeScheduler: promoting {} PENDING payment(s) to OVERDUE", candidates.size());
        int skipped = 0;
        for (Payment p : candidates) {
            // Audit H23: skip rows whose dueDate slipped through the
            // service-level non-null guard (legacy data). Without
            // this the next ChronoUnit.DAYS.between call NPEs and
            // halts the entire batch — meaning every OTHER overdue
            // payment in the run also misses its fee bump.
            if (p.getDueDate() == null || p.getAmount() == null) {
                log.warn("LateFeeScheduler: skipping paymentId={} (null dueDate or amount)", p.getId());
                skipped++;
                continue;
            }

            long daysOverdue = ChronoUnit.DAYS.between(p.getDueDate(), today);
            BigDecimal newFee = computeLateFee(p.getAmount(), daysOverdue);
            p.setLateFee(newFee);
            p.setStatus(PaymentStatus.OVERDUE);
            p.setTotalAmount(p.getAmount().add(newFee));
            repo.save(p);

            // Audit H24: stage the event in the OUTBOX (same
            // transaction as the OVERDUE flip), not Kafka directly.
            // OutboxPublisher's scheduled job pushes pending rows
            // to Kafka every 30s — if the broker is unreachable
            // right now the row stays PENDING and the publisher
            // retries until it lands. The audit's "Kafka down +
            // payment flipped = silent" gap is closed: either both
            // the state change AND the outbox row commit, or
            // neither does.
            outbox.stageOverdue(PaymentOverdueEvent.builder()
                    .eventType("payment.overdue")
                    .paymentId(p.getId())
                    .tenantId(p.getTenantId())
                    .amount(p.getAmount())
                    .lateFee(newFee)
                    .daysOverdue(daysOverdue)
                    .timestamp(Instant.now())
                    .build());
        }
        if (skipped > 0) {
            log.warn("LateFeeScheduler: {} payment(s) skipped due to null fields", skipped);
        }
    }

    /**
     * Existing OVERDUE rows — recompute fee from days-overdue,
     * idempotent. Saves only when the value actually changes so
     * we don't generate update-event spam.
     */
    private void growExistingOverdue(LocalDate today) {
        // Reuse the same query with status=OVERDUE and dueDate < today
        // (the OVERDUE rows already have dueDate < today so this just
        // returns all OVERDUE rows).
        List<Payment> overdue = repo.findOverdueCandidates(PaymentStatus.OVERDUE, today);
        for (Payment p : overdue) {
            // H23 — same null-skip protection as promotePending.
            if (p.getDueDate() == null || p.getAmount() == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(p.getDueDate(), today);
            BigDecimal recomputed = computeLateFee(p.getAmount(), daysOverdue);
            if (recomputed.compareTo(p.getLateFee() == null ? BigDecimal.ZERO : p.getLateFee()) != 0) {
                p.setLateFee(recomputed);
                p.setTotalAmount(p.getAmount().add(recomputed));
                repo.save(p);
            }
        }
    }

    /**
     * lateFee = rent × (weekly% / 100) × ceil(days / 7), capped at
     * rent × (maxPercent / 100). Capping is hard — once you hit the
     * ceiling, the fee freezes; further delay doesn't grow it.
     *
     * <p>Audit M12: ALL arithmetic stays in BigDecimal. The previous
     * implementation used {@code weeklyPercent / 100.0} (double
     * divide) before multiplying into BigDecimal, which silently
     * introduced IEEE-754 drift — for rent=10000 and weeklyPercent=2,
     * the expected ₹200.00 sometimes came out ₹199.9999… and rounded
     * to ₹199.99 instead of ₹200.00. With money math, that's a bug.
     */
    private BigDecimal computeLateFee(BigDecimal rent, long daysOverdue) {
        if (rent == null || daysOverdue <= 0) return BigDecimal.ZERO;
        long weeks = (daysOverdue + 6) / 7;
        BigDecimal hundred = BigDecimal.valueOf(100);
        // Use a generous intermediate scale (10) so the ratio doesn't
        // round prematurely; the final result is forced to 2dp HALF_UP.
        BigDecimal weeklyRatio = BigDecimal.valueOf(weeklyPercent)
                .divide(hundred, 10, RoundingMode.HALF_UP);
        BigDecimal capRatio = BigDecimal.valueOf(maxPercent)
                .divide(hundred, 10, RoundingMode.HALF_UP);
        BigDecimal raw = rent.multiply(weeklyRatio).multiply(BigDecimal.valueOf(weeks));
        BigDecimal cap = rent.multiply(capRatio);
        BigDecimal capped = raw.min(cap);
        return capped.setScale(2, RoundingMode.HALF_UP);
    }
}
