package com.spa.home_rental_application.auth_service.Dto.Response;

import java.time.Instant;

/**
 * One row of the {@code /admin/settings} list. Mirrors the
 * {@code SystemSetting} entity but never includes anything the
 * admin UI can't render — fine to add fields here as new toggles
 * arrive.
 */
public record SystemSettingResponse(
        String settingKey,
        String value,
        Instant updatedAt,
        Long updatedBy
) {}
