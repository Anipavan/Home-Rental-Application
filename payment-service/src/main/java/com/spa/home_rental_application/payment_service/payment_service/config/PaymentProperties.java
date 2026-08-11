package com.spa.home_rental_application.payment_service.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PaymentProperties {
    /** Active gateway implementation: mock | razorpay | stripe */
    private String gateway = "mock";

    /** Late-fee accrual rate (percent of base amount per overdue week). */
    private BigDecimal lateFeePercentPerWeek = new BigDecimal("2.0");

    /** Cap on total accrued late fee (percent of base amount). */
    private BigDecimal maxLateFeePercent = new BigDecimal("10.0");

    /**
     * @deprecated superseded by {@link #reminderOffsetsBeforeDue}, which
     * supports multiple pre-due offsets (5 days, 3 days, 1 day, on-due-day)
     * instead of a single fire. Retained so environments with an
     * override in application.yml still bind cleanly; no code path reads it.
     */
    @Deprecated
    private int reminderDaysBeforeDue = 3;

    /**
     * Days-before-due offsets on which the reminder scheduler fires
     * a {@code payment.reminder} event. One reminder per offset per
     * payment — a tenant with a payment 5 days out gets nudged on
     * D-5, D-3, D-1, and D-0. Configurable so ops can tighten or
     * loosen the cadence without a code change.
     */
    private List<Integer> reminderOffsetsBeforeDue = List.of(5, 3, 1, 0);

    /**
     * Days-overdue offsets on which the overdue-nudge scheduler
     * re-fires a {@code payment.overdue} event. The initial
     * transition to OVERDUE always fires once on D+0 from
     * {@code sweepOverdue}; these are the follow-up nudges. Empty
     * list disables overdue reminders entirely.
     */
    private List<Integer> overdueNudgeOffsets = List.of(3, 7, 14);
}
