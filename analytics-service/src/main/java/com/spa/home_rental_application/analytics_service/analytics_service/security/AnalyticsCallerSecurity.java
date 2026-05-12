package com.spa.home_rental_application.analytics_service.analytics_service.security;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Per-request authorization helper for analytics endpoints. Reads the
 * caller's identity off the gateway-stamped {@code X-Auth-User-Id}
 * header + the granted authorities populated by
 * {@link GatewayAuthFilter}.
 *
 * <p>Mirrors the {@code CallerSecurity} helpers in property-service and
 * payment-service — same shape, same fall-through behaviour for
 * system-level callers (Kafka consumers, scheduled aggregations).
 */
@Slf4j
public final class AnalyticsCallerSecurity {

    private AnalyticsCallerSecurity() {}

    /**
     * Refuse unless the caller equals {@code targetUserId} or is admin.
     * System calls (no gateway header) pass through.
     */
    public static void requireSelfOrAdmin(String targetUserId, HttpServletRequest req) {
        if (isAdmin()) return;
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) return;
        if (targetUserId == null || !targetUserId.equals(caller)) {
            log.warn("Forbidden: caller={} attempted analytics on userId={}", caller, targetUserId);
            throw new AccessDeniedException("You can only view your own analytics.");
        }
    }

    /** Refuse unless the caller has admin role. System calls pass through. */
    public static void requireAdmin(HttpServletRequest req) {
        if (isAdmin()) return;
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) return;
        log.warn("Forbidden: non-admin caller={} attempted cross-tenant analytics", caller);
        throw new AccessDeniedException("Admin role required.");
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) return true;
        }
        return false;
    }
}
