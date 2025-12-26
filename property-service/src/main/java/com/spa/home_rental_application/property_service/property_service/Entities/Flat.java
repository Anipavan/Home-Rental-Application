package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;

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
public class Flat {

    @Id
    private String id;

    @Column(name = "building_id", nullable = false)
    private String buildingId;

    @Column(name = "flat_number", nullable = false)
    private String flatNumber;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "bedrooms")
    private Integer bedrooms;

    @Column(name = "bathrooms")
    private Integer bathrooms;

    @Column(name = "area_sqft")
    private Double areaSqft;

    @Column(name = "rent_amount")
    private BigDecimal rentAmount;

    @Column(name = "is_occupied",nullable = false)
    @Builder.Default
    private Boolean isOccupied=false;

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
    @Override
    public String toString() {
        return "Flat{" +
                "id=" + id +
                ", buildingId='" + buildingId + '\'' +
                ", flatNumber='" + flatNumber + '\'' +
                ", floor=" + floor +
                ", bedrooms=" + bedrooms +
                ", bathrooms=" + bathrooms +
                ", areaSqft=" + areaSqft +
                ", rentAmount=" + rentAmount +
                ", isOccupied=" + isOccupied +
                ", tenantId='" + tenantId + '\'' +
                ", leaseStartDate=" + leaseStartDate +
                ", leaseEndDate=" + leaseEndDate +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
