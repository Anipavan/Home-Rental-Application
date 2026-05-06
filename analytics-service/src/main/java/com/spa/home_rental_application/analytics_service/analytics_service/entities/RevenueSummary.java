package com.spa.home_rental_application.analytics_service.analytics_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-owner monthly revenue rollup. Updated incrementally by
 * {@code AggregationService} as payment.completed / payment.overdue events
 * arrive. One row per (owner, year, month).
 */
@Entity
@Table(name = "revenue_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_revenue_owner_year_month",
                columnNames = {"owner_id", "year", "month"}),
        indexes = {
                @Index(name = "idx_revenue_owner", columnList = "owner_id"),
                @Index(name = "idx_revenue_year_month", columnList = "year,month")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(name = "total_revenue", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_paid", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "total_pending", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalPending = BigDecimal.ZERO;

    @Column(name = "total_overdue", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalOverdue = BigDecimal.ZERO;

    @Column(name = "payment_count", nullable = false)
    @Builder.Default
    private long paymentCount = 0;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @PrePersist @PreUpdate
    void touch() { generatedAt = Instant.now(); }
}
