package com.spa.home_rental_application.analytics_service.analytics_service.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-owner monthly payment behaviour: on-time vs late payments and the
 * average lateness. Drives the "collection efficiency" reports.
 */
@Entity
@Table(name = "payment_trends",
        uniqueConstraints = @UniqueConstraint(name = "uk_trends_owner_year_month",
                columnNames = {"owner_id", "year", "month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(name = "on_time_payments", nullable = false)
    @Builder.Default
    private long onTimePayments = 0;

    @Column(name = "late_payments", nullable = false)
    @Builder.Default
    private long latePayments = 0;

    /** Sum of (paidDate - dueDate) days across late payments. */
    @Column(name = "total_delay_days", nullable = false)
    @Builder.Default
    private long totalDelayDays = 0;

    public double getAvgDelayDays() {
        return latePayments == 0 ? 0.0 : (double) totalDelayDays / latePayments;
    }

    public double getCollectionRate() {
        long total = onTimePayments + latePayments;
        return total == 0 ? 1.0 : (double) onTimePayments / total;
    }
}
