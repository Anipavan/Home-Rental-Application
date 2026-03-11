package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "flats")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE flats SET is_deleted = 1 WHERE id=?")
@Where(clause = "is_deleted = 0")
public class Flat {

    @Id
    private String id;

    @Column(name = "building_id", nullable = false)
    private String buildingId;

    @Column(name = "flat_number", nullable = false)
    private String flatNumber;

    private Integer floor;

    private Integer bedrooms;

    private Integer bathrooms;

    @Column(name = "area_sqft")
    private Double areaSqft;

    @Column(name = "rent_amount")
    private BigDecimal rentAmount;

    @Column(name = "is_occupied", nullable = false)
    @Builder.Default
    private Boolean isOccupied = false;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "lease_start_date")
    private LocalDate leaseStartDate;

    @Column(name = "lease_end_date")
    private LocalDate leaseEndDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}