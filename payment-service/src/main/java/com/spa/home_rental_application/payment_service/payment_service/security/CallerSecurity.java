package com.spa.home_rental_application.payment_service.payment_service.security;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.payment_service.payment_service.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Reads the caller's identity off the current HTTP request (gateway-stamped
 * {@code X-Auth-User-Id} header). Mirrors the property-service helper so
 * the two services have the same per-request authorization shape.
 *
 * <p>Audit C5 — every payment endpoint previously trusted the path
 * parameter without verifying the caller actually owned the payment.
 * The static helpers below are the single choke-point for that check.
 *
 * <p>Calls made outside a servlet request (Kafka listeners, scheduled
 * jobs, unit tests) bypass the guard — those run as the system itself
 * and shouldn't be confused with an external caller.
 */
@Slf4j
public final class CallerSecurity {

    private CallerSecurity() {}

    /** Read the {@code X-Auth-User-Id} header off the current request, if any. */
    public static Optional<String> getCurrentAuthUserId() {
        return currentRequest()
                .map(r -> r.getHeader(GatewayAuthFilter.HDR_UID))
                .filter(s -> !s.isBlank());
    }

    /** True when the current caller has the ADMIN authority. */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Throws {@link ForbiddenException} unless the caller is the tenant,
     * the owner, or an admin. Used to gate per-payment endpoints — a
     * payment is visible to (a) its tenant, (b) its owner (= building's
     * landlord), or (c) any admin. Everyone else gets a 403.
     */
    public static void requireTenantOwnerOrAdmin(String tenantId, String ownerId) {
        Optional<String> caller = getCurrentAuthUserId();
        if (caller.isEmpty()) {
            // System-level (scheduled job, internal Feign with no
            // forwarded header) — bypass the check. The gateway is
            // the single place we trust to stamp the header on real
            // user traffic.
            return;
        }
        if (isAdmin()) return;
        String c = caller.get();
        if (tenantId != null && tenantId.equals(c)) return;
        if (ownerId  != null && ownerId.equals(c))  return;

        log.warn("Forbidden: caller={} attempted to access payment of tenant={} owner={}",
                c, tenantId, ownerId);
        // 403 with a generic copy — never leak which-of-tenant-or-owner
        // the caller failed to match.
        throw new ForbiddenException("You don't have access to this payment.");
    }

    /**
     * Throws unless the caller's id equals {@code targetUserId} or the
     * caller is an admin. Used for the per-user listing endpoints
     * (GET /payments/tenant/{tenantId}, /payments/owner/{ownerId}).
     */
    public static void requireSelfOrAdmin(String targetUserId) {
        Optional<String> caller = getCurrentAuthUserId();
        if (caller.isEmpty()) return;
        if (isAdmin()) return;
        if (targetUserId != null && targetUserId.equals(caller.get())) return;

        log.warn("Forbidden: caller={} attempted access to payments of {}",
                caller.get(), targetUserId);
        throw new ForbiddenException("You can only view your own payments.");
    }

    /** True if the caller is an admin. */
    public static void requireAdmin() {
        Optional<String> caller = getCurrentAuthUserId();
        if (caller.isEmpty()) return;
        if (isAdmin()) return;
        log.warn("Forbidden: non-admin caller={} attempted admin endpoint", caller.get());
        throw new ForbiddenException("Admin role required.");
    }

    private static Optional<HttpServletRequest> currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return Optional.ofNullable(attrs).map(ServletRequestAttributes::getRequest);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
