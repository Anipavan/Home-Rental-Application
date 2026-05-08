package com.spa.home_rental_application.lease_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Append-only audit log for every lease transition. */
@Entity
@Table(name = "lease_history",
        indexes = @Index(name = "ix_lease_history_lease", columnList = "lease_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "lease_id", nullable = false)
    private String leaseId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;            // CREATED | SIGNED | RENEWED | AMENDED | TERMINATED | EXPIRY_WARNING

    @Column(name = "previous_rent", precision = 12, scale = 2)
    private BigDecimal previousRent;

    @Column(name = "new_rent", precision = 12, scale = 2)
    private BigDecimal newRent;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}
