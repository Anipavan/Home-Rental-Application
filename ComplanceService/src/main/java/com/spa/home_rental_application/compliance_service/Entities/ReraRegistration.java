package com.spa.home_rental_application.compliance_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RERA registration record per property + state. Each Indian state runs
 * its own RERA portal with its own number format, so {@code state} is a
 * required field and we don't try to make registration numbers unique
 * across states.
 */
@Entity
@Table(name = "rera_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rera_property_state", columnNames = {"property_id", "state"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReraRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "property_id", nullable = false)
    private String propertyId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "state", length = 50, nullable = false)
    private String state;                 // KARNATAKA | MAHARASHTRA | ...

    @Column(name = "rera_registration_number", length = 100)
    private String reraRegistrationNumber;

    @Column(name = "rera_portal_id", length = 100)
    private String reraPortalId;

    @Column(name = "registration_status", length = 20, nullable = false)
    @Builder.Default
    private String registrationStatus = "PENDING";   // PENDING | REGISTERED | EXPIRED

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
