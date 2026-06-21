package com.spa.home_rental_application.auth_service.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Platform-wide feature toggle. One row per key. Currently only
 * {@code maintainer_payment_enabled} is in use; future global toggles
 * (e.g. signup throttling, owner-trial enable, etc.) land here as
 * additional rows with their own keys — no schema churn per toggle.
 *
 * <p>{@link SystemSettingsService} layers a 60-second cache on top
 * so the per-request reads from
 * {@code MaintainerPaymentController} don't hammer the DB; the
 * cache invalidates whenever an admin flips a value, so the new
 * setting takes effect within one cache TTL at most.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 60, nullable = false)
    private String settingKey;

    @Column(name = "value", length = 255, nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Auth user id of the admin who last flipped this setting.
     * Null on the seed row (no human touched it). The
     * /admin/settings PUT endpoint stamps the caller's authUserId
     * here on every change.
     */
    @Column(name = "updated_by")
    private Long updatedBy;
}
