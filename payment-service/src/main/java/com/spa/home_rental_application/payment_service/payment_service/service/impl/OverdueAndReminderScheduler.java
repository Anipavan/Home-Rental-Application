package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentOverdueEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentReminderEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.enums.ReminderType;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Three scheduled jobs that drive automated payment lifecycle nudges:
 *
 * <ol>
 *   <li><b>Overdue sweep</b> — daily at 02:00. Marks any PENDING payment
 *       past its due date as OVERDUE, accrues a late fee, and fires
 *       {@code payment.overdue} once as the entry event.</li>
 *   <li><b>Pre-due reminder sweep</b> — daily at 09:00. For each configured
 *       offset in {@link PaymentProperties#getReminderOffsetsBeforeDue()}
 *       (default D-5, D-3, D-1, D-0), fires {@code payment.reminder} to
 *       tenants whose PENDING invoice is due exactly {@code offset} days
 *       from today. Notification Service consumes these and fans across
 *       email + SMS + WhatsApp + in-app.</li>
 *   <li><b>Overdue-nudge sweep</b> — daily at 09:15. For each offset in
 *       {@link PaymentProperties#getOverdueNudgeOffsets()} (default D+3,
 *       D+7, D+14), re-fires {@code payment.overdue} to tenants whose
 *       OVERDUE invoice was originally due {@code offset} days ago. This
 *       closes the gap the previous single-shot design left: without
 *       these follow-ups, a tenant who ignored the first overdue email
 *       never heard about the debt again.</li>
 * </ol>
 *
 * <p>All three run at IST via the JVM's server timezone — the containers
 * boot with {@code TZ=Asia/Kolkata} so 09:00 cron == 09:00 IST.
 */
@Component
@Slf4j
public class OverdueAndReminderScheduler {

    private final PaymentRepository paymentRepo;
    private final PaymentServiceEvents events;
    private final PaymentServiceImpl service;   // for computeLateFee()
    private final PaymentProperties props;

    public OverdueAndReminderScheduler(PaymentRepository paymentRepo,
                                       PaymentServiceEvents events,
                                       PaymentServiceImpl service,
                                       PaymentProperties props) {
        this.paymentRepo = paymentRepo;
        this.events = events;
        this.service = service;
        this.props = props;
    }

    /** Daily at 02:00 server time. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void sweepOverdue() {
        LocalDate today = LocalDate.now();
        List<Payment> due = paymentRepo.findOverdueCandidates(PaymentStatus.PENDING, today);
        for (Payment p : due) {
            long daysOverdue = ChronoUnit.DAYS.between(p.getDueDate(), today);
            BigDecimal lateFee = service.computeLateFee(p.getAmount(), daysOverdue);
            p.setLateFee(lateFee);
            p.setTotalAmount(p.getAmount().add(lateFee));
            p.setStatus(PaymentStatus.OVERDUE);
            paymentRepo.save(p);

            events.sendPaymentOverdue(PaymentOverdueEvent.builder()
                    .eventType("payment.overdue")
                    .paymentId(p.getId())
                    .tenantId(p.getTenantId())
                    .daysOverdue(daysOverdue)
                    .amount(p.getAmount())
                    .lateFee(lateFee)
                    .timestamp(Instant.now())
                    .build());
        }
        if (!due.isEmpty()) log.info("Overdue sweep marked {} payment(s) as OVERDUE", due.size());
    }

    /**
     * Pre-due reminders. Runs daily at 09:00 and iterates every
     * configured offset in {@link PaymentProperties#getReminderOffsetsBeforeDue()}.
     * A payment created 5 days out gets one reminder on each of
     * D-5, D-3, D-1, and D-0 — no duplicates within a day because each
     * offset picks a distinct target date.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendReminders() {
        LocalDate today = LocalDate.now();
        List<Integer> offsets = props.getReminderOffsetsBeforeDue();
        if (offsets == null || offsets.isEmpty()) {
            log.debug("Reminder sweep skipped — no offsets configured");
            return;
        }
        int total = 0;
        for (Integer offset : offsets) {
            LocalDate target = today.plusDays(offset);
            List<Payment> due = paymentRepo.findPendingDueOn(target);
            for (Payment p : due) {
                // Prefer totalAmount (base + accrued fee, though pre-due
                // it usually == base) so late-adjusted invoices reminded
                // right at D-0 still show the correct payable. Fall back
                // to base amount for older rows where totalAmount wasn't
                // populated.
                BigDecimal payable = p.getTotalAmount() != null
                        ? p.getTotalAmount()
                        : p.getAmount();
                events.sendPaymentReminder(PaymentReminderEvent.builder()
                        .eventType("payment.reminder")
                        .paymentId(p.getId())
                        .tenantId(p.getTenantId())
                        .reminderType(ReminderType.EMAIL.name())
                        .daysUntilDue(offset)
                        .amount(payable)
                        .dueDate(p.getDueDate())
                        .timestamp(Instant.now())
                        .build());
            }
            total += due.size();
            if (!due.isEmpty()) {
                log.info("Reminder sweep dispatched {} reminder(s) for D-{}",
                        due.size(), offset);
            }
        }
        if (total > 0) {
            log.info("Reminder sweep totals: {} reminder(s) across {} offset(s)",
                    total, offsets.size());
        }
    }

    /**
     * Overdue follow-ups. Runs daily at 09:15 (staggered off the
     * pre-due sweep so log lines don't interleave). For each offset in
     * {@link PaymentProperties#getOverdueNudgeOffsets()}, finds OVERDUE
     * payments whose dueDate was exactly that many days ago and re-fires
     * {@code payment.overdue} with the current daysOverdue count.
     *
     * <p>No state change on the payment — late fee stays what it was
     * when the initial sweep flipped it. This scheduler is purely a
     * "your rent is still overdue" nudge.
     */
    @Scheduled(cron = "0 15 9 * * *")
    public void sendOverdueNudges() {
        LocalDate today = LocalDate.now();
        List<Integer> offsets = props.getOverdueNudgeOffsets();
        if (offsets == null || offsets.isEmpty()) {
            log.debug("Overdue-nudge sweep skipped — no offsets configured");
            return;
        }
        int total = 0;
        for (Integer offset : offsets) {
            LocalDate target = today.minusDays(offset);
            List<Payment> nudges = paymentRepo.findOverdueDueOn(target);
            for (Payment p : nudges) {
                BigDecimal lateFee = p.getLateFee() != null
                        ? p.getLateFee()
                        : BigDecimal.ZERO;
                events.sendPaymentOverdue(PaymentOverdueEvent.builder()
                        .eventType("payment.overdue")
                        .paymentId(p.getId())
                        .tenantId(p.getTenantId())
                        .daysOverdue((long) offset)
                        .amount(p.getAmount())
                        .lateFee(lateFee)
                        .timestamp(Instant.now())
                        .build());
            }
            total += nudges.size();
            if (!nudges.isEmpty()) {
                log.info("Overdue-nudge sweep dispatched {} nudge(s) for D+{}",
                        nudges.size(), offset);
            }
        }
        if (total > 0) {
            log.info("Overdue-nudge sweep totals: {} nudge(s) across {} offset(s)",
                    total, offsets.size());
        }
    }
}
