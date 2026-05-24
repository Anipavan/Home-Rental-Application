package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An owner's review of a tenant's tenancy. Stored against the (owner,
 * tenant, flat) triple so the same tenant can have separate reviews from
 * each owner they've leased from.
 */
@Entity
@Table(name = "tenant_reviews", indexes = {
        @Index(name = "idx_review_tenant", columnList = "tenant_id"),
        @Index(name = "idx_review_owner",  columnList = "owner_id"),
        @Index(name = "idx_review_flat",   columnList = "flat_id")
})
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
public class TenantReview {

    @Id private String id;

    @Column(name = "owner_id",  nullable = false) private String ownerId;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "flat_id",   nullable = false) private String flatId;
    @Column(name = "building_id")                 private String buildingId;

    /** 1-5 stars. */
    @Column(nullable = false) private Integer rating;

    // `comment` is a reserved word in Oracle — using it unquoted in
    // DDL fails with ORA-03050. Explicit @Column(name=...) renames
    // the DB column to a non-reserved identifier. Java field stays
    // `comment` so no callers need to change.
    @Column(name = "review_comment", length = 1000) private String comment;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
