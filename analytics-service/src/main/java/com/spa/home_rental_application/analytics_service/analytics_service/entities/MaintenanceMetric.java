package com.spa.home_rental_application.analytics_service.analytics_service.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Aggregate maintenance metrics by category. One row per category;
 * updated incrementally on every {@code maintenance.resolved} event.
 */
@Entity
@Table(name = "maintenance_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uk_maint_category", columnNames = {"category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "resolved_count", nullable = false)
    @Builder.Default
    private long resolvedCount = 0;

    /** Sum of resolution times in minutes across all resolved requests. */
    @Column(name = "total_resolution_minutes", nullable = false)
    @Builder.Default
    private long totalResolutionMinutes = 0;

    public double getAvgResolutionMinutes() {
        return resolvedCount == 0 ? 0.0 : (double) totalResolutionMinutes / resolvedCount;
    }
}
