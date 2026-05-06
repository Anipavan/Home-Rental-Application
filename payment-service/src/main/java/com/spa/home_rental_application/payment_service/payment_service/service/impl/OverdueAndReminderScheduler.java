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
 * Two scheduled jobs that drive automated payment lifecycle nudges:
 *
 * <ol>
 *   <li><b>Overdue sweep</b> — runs daily at 02:00. Marks any PENDING payment
 *       past its due date as OVERDUE, accrues a late fee, and fires
 *       {@code payment.overdue}.</li>
 *   <li><b>Reminder sweep</b> — runs daily at 09:00. For every PENDING
 *       payment due in {@code app.payment.reminder-days-before-due} days,
 *       fires {@code payment.reminder}. Notification Service consumes these
 *       and emails / SMSes / push-notifies the tenant.</li>
 * </ol>
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

    /** Daily at 09:00 server time. */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendReminders() {
        LocalDate target = LocalDate.now().plusDays(props.getReminderDaysBeforeDue());
        List<Payment> due = paymentRepo.findPendingDueOn(target);
        for (Payment p : due) {
            events.sendPaymentReminder(PaymentReminderEvent.builder()
                    .eventType("payment.reminder")
                    .paymentId(p.getId())
                    .tenantId(p.getTenantId())
                    .reminderType(ReminderType.EMAIL.name())
                    .daysUntilDue(props.getReminderDaysBeforeDue())
                    .timestamp(Instant.now())
                    .build());
        }
        if (!due.isEmpty()) log.info("Reminder sweep dispatched {} reminder(s)", due.size());
    }
}
