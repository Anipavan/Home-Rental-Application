package com.spa.home_rental_application.analytics_service.analytics_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Per-building snapshot of occupancy on a given day. Updated by the
 * flat.occupied / flat.vacated listeners. We store running counts so a
 * dashboard can plot occupancy over time without recomputing from event
 * history.
 */
@Entity
@Table(name = "occupancy_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_occupancy_building_date",
                columnNames = {"building_id", "stat_date"}),
        indexes = {
                @Index(name = "idx_occupancy_building", columnList = "building_id"),
                @Index(name = "idx_occupancy_date", columnList = "stat_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupancyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "building_id", nullable = false)
    private String buildingId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "total_flats", nullable = false)
    @Builder.Default
    private int totalFlats = 0;

    @Column(name = "occupied_flats", nullable = false)
    @Builder.Default
    private int occupiedFlats = 0;

    @Column(name = "vacant_flats", nullable = false)
    @Builder.Default
    private int vacantFlats = 0;

    /** 0.0 – 1.0 (e.g. 0.85 = 85% occupancy). */
    @Column(name = "occupancy_rate", nullable = false)
    @Builder.Default
    private double occupancyRate = 0.0;
}
