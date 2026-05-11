package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A tenant's saved (wishlisted) flat. Each row is a single
 * {@code (userId, flatId)} pair; a unique constraint on that pair
 * keeps the toggle idempotent.
 *
 * <p>{@code userId} stores the auth-tier id (JWT subject), not the
 * user-service primary id, so:
 *  - we don't depend on a user-service profile row existing
 *  - the favourite survives any future user-service row rebuild
 *  - the gateway-supplied {@code X-Auth-User-Id} maps in directly
 *
 * <p>Sized for a heart-toggle on every flat card. The unique index
 * keeps the "is X favourited" lookup an indexed equality.
 */
@Entity
@Table(
        name = "flat_favorites",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fav_user_flat",
                columnNames = {"user_id", "flat_id"}
        ),
        indexes = {
                @Index(name = "idx_fav_user", columnList = "user_id"),
                @Index(name = "idx_fav_flat", columnList = "flat_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FlatFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "flat_id", nullable = false, length = 64)
    private String flatId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
