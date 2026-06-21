package com.spa.home_rental_application.auth_service.Controller;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.auth_service.Dto.Request.SetMaintainerPaymentEnabledRequest;
import com.spa.home_rental_application.auth_service.Dto.Response.SystemSettingResponse;
import com.spa.home_rental_application.auth_service.Service.SystemSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin reads + writes for the global {@code system_settings}
 * toggles. ADMIN role required on every route; the gateway-stamped
 * X-Auth-Roles header drives Spring Security's {@code hasRole}
 * check.
 *
 * <p>Only the {@code maintainer_payment_enabled} toggle is wired
 * today. Future toggles get their own endpoints here, or a generic
 * {@code PUT /admin/settings/{key}} once we have more than a couple.
 */
@RestController
@RequestMapping(value = "/auth/admin/settings", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Admin Settings",
        description = "Global feature toggles (ADMIN only)")
public class SystemSettingsController {

    private final SystemSettingsService settingsService;

    public SystemSettingsController(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Operation(summary = "List every system_settings row.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SystemSettingResponse>> listAll() {
        return ResponseEntity.ok(settingsService.listAll());
    }

    @Operation(summary = "Flip the maintainer activation-fee gate on/off.")
    @PutMapping(value = "/maintainer-payment-enabled", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemSettingResponse> setMaintainerPaymentEnabled(
            @RequestHeader(GatewayAuthFilter.HDR_UID) Long adminUserId,
            @Valid @RequestBody SetMaintainerPaymentEnabledRequest req) {
        log.info("PUT /admin/settings/maintainer-payment-enabled enabled={} by adminUserId={}",
                req.enabled(), adminUserId);
        settingsService.setMaintainerPaymentEnabled(req.enabled(), adminUserId);
        return ResponseEntity.ok(
                settingsService.listAll().stream()
                        .filter(s -> "maintainer_payment_enabled".equals(s.settingKey()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "system_settings row vanished after write")));
    }
}
