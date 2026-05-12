package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.PreferenceRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.PreferenceResponse;
import com.spa.home_rental_application.notification_service.notification_service.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Per-user notification channel preferences (email / SMS / WhatsApp /
 * push / muted categories).
 *
 * <p>Audit C8 — previously every endpoint accepted an arbitrary
 * {@code userId} path parameter, letting any authenticated user
 * overwrite anyone's recipient address. Every method below now
 * enforces "caller must equal {@code userId} or be an admin" via
 * {@link #requireSelfOrAdmin}.
 */
@RestController
@RequestMapping(value = "/notifications/preferences", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notification Preferences", description = "Per-user channel preferences and category opt-outs")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @Operation(summary = "Get a user's notification preferences (self or ADMIN)")
    @GetMapping("/{userId}")
    public ResponseEntity<PreferenceResponse> get(@PathVariable String userId,
                                                  HttpServletRequest req) {
        requireSelfOrAdmin(userId, req);
        return ResponseEntity.ok(service.get(userId));
    }

    @Operation(summary = "Upsert a user's notification preferences (self or ADMIN)")
    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PreferenceResponse> upsert(@PathVariable String userId,
                                                     @Valid @RequestBody PreferenceRequest body,
                                                     HttpServletRequest req) {
        requireSelfOrAdmin(userId, req);
        return ResponseEntity.ok(service.upsert(userId, body));
    }

    private static void requireSelfOrAdmin(String targetUserId, HttpServletRequest req) {
        if (isAdmin()) return;
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) {
            // System-level call (internal Feign, listener seeding
            // preferences from a Kafka event) — allow. The gateway is
            // the single trusted stamper for real user traffic.
            return;
        }
        if (targetUserId == null || !targetUserId.equals(caller)) {
            throw new AccessDeniedException(
                    "You can only manage your own notification preferences.");
        }
    }

    private static boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) return true;
        }
        return false;
    }
}
