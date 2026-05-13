package com.spa.home_rental_application.property_service.property_service.security;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Reads the caller's identity off the current HTTP request — what the
 * gateway pre-populated via {@link GatewayAuthFilter}.
 *
 * <p>Two pieces of data:
 * <ul>
 *   <li><b>authUserId</b> — pulled from the {@code X-Auth-User-Id}
 *       header. This is the value {@code Building.ownerId} /
 *       {@code Flat.tenantId} key off, so it's the right id to use for
 *       ownership checks.</li>
 *   <li><b>role</b> — read from {@link SecurityContextHolder} where the
 *       gateway-auth filter already populated the granted authorities.
 *       We look for ADMIN specifically, since admins should be able to
 *       perform any owner-scoped operation.</li>
 * </ul>
 *
 * <p>Calls made <em>outside</em> a servlet request context (e.g. Kafka
 * listeners triggering business logic, scheduled jobs, unit tests) hit
 * the no-context path: {@link #getCurrentAuthUserId()} returns
 * {@link Optional#empty}, and {@link #requireOwnerOrAdmin(String)}
 * becomes a no-op. That's the right semantics — those paths run as
 * the system, not on behalf of an external caller, and the gateway-
 * fronted HTTP entrypoints are where we want the guard.
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
     * Throws {@link ForbiddenException} unless the current caller is the
     * given {@code ownerId} or has the ADMIN role. No-ops when there's
     * no current request (Kafka, jobs, tests).
     */
    public static void requireOwnerOrAdmin(String ownerId) {
        Optional<String> caller = getCurrentAuthUserId();
        if (caller.isEmpty()) {
            // No request context — assume system-level call, allow.
            return;
        }
        if (isAdmin()) return;
        if (ownerId != null && ownerId.equals(caller.get())) return;

        log.warn("Forbidden: caller={} attempted owner action on ownerId={}",
                caller.get(), ownerId);
        throw new ForbiddenException(
                "You can only manage buildings and flats you own.");
    }

    /**
     * Throws {@link ForbiddenException} unless the current caller is
     * {@code userId} themselves or has the ADMIN role. Used by the
     * tenant-initiated scheduled-vacate path (Issue #5) where the
     * action is only valid if performed by the tenant who actually
     * lives in the flat. No-ops when there's no current request
     * (scheduler, Kafka, tests).
     */
    public static void requireSelfOrAdmin(String userId) {
        Optional<String> caller = getCurrentAuthUserId();
        if (caller.isEmpty()) {
            // No request context — assume system-level call, allow.
            return;
        }
        if (isAdmin()) return;
        if (userId != null && userId.equals(caller.get())) return;

        log.warn("Forbidden: caller={} attempted self-only action on userId={}",
                caller.get(), userId);
        throw new ForbiddenException(
                "You can only perform this action for your own account.");
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
