package com.spa.home_rental_application.auth_service.Controller;

import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.MessageResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Service.AuthService;
import com.spa.home_rental_application.auth_service.Util.RefreshCookie;
import com.spa.home_rental_application.auth_service.enums.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Auth", description = "Authentication, registration, token lifecycle, password reset")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user (publishes user.registered)")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        log.info("POST /auth/register userName={} role={}", req.userName(), req.userRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @Operation(summary = "Log in. Sets hra_refresh HttpOnly cookie + returns access JWT")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletRequest httpReq) {
        log.info("POST /auth/login userName={}", req.userName());
        AuthResponse resp = authService.login(req,
                clientIp(httpReq), httpReq.getHeader("User-Agent"));
        return withRefreshCookie(resp);
    }

    @Operation(summary = "Rotate refresh token — reads hra_refresh cookie, sets a fresh one")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshTokenRequest req,
                                                HttpServletRequest httpReq) {
        // Prefer the cookie (browsers); fall back to body (non-browser
        // clients / legacy) so nothing sudden-breaks during rollout.
        String refresh = RefreshCookie.read(httpReq);
        if (refresh == null && req != null) refresh = req.refreshToken();
        if (refresh == null || refresh.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Pass through client fingerprint so AuthService can enforce
        // the H5 IP/UA binding on the stored refresh token.
        AuthResponse resp = authService.refresh(new RefreshTokenRequest(refresh),
                clientIp(httpReq), httpReq.getHeader("User-Agent"));
        return withRefreshCookie(resp);
    }

    @Operation(summary = "Log out. Reads hra_refresh cookie, revokes it, clears the cookie")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> logout(@RequestBody(required = false) LogoutRequest req,
                                                  HttpServletRequest httpReq) {
        String refresh = RefreshCookie.read(httpReq);
        if (refresh == null && req != null) refresh = req.refreshToken();
        if (refresh != null && !refresh.isBlank()) {
            authService.logout(new LogoutRequest(refresh));
        }
        // Always clear the cookie even when we can't identify the
        // token — logout is idempotent + browser-side cleanup happens
        // regardless of server-side revocation success.
        HttpHeaders headers = new HttpHeaders();
        RefreshCookie.clearHeader(headers);
        return ResponseEntity.ok().headers(headers).body(new MessageResponse("Logged out"));
    }

    /** Delegate to {@link RefreshCookie#wrap} for browser-safe cookie packaging. */
    private static ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse resp) {
        return RefreshCookie.wrap(resp);
    }

    @Operation(summary = "Begin a forgot-password flow. Always 200 — does not reveal whether the email exists")
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.startPasswordReset(req);
        return ResponseEntity.ok(new MessageResponse(
                "If the email is registered, a reset link has been sent."));
    }

    @Operation(summary = "Complete a forgot-password flow with the emailed token")
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.completePasswordReset(req);
        return ResponseEntity.ok(new MessageResponse("Password updated"));
    }

    /**
     * Used by user-service ({@code AuthServiceFeig.getUserByRole}) to
     * power the owner-side "Assign tenant" picker. Owners need this
     * call to surface every TENANT-role auth user, not just admins —
     * follows the same hasAnyRole pattern as the lookupById endpoint
     * below.
     */
    @Operation(summary = "List users with the given role (ADMIN + OWNER)")
    @GetMapping("/role/{roleName}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<List<AuthUserResponse>> getUserByRole(@PathVariable String roleName) {
        Roles role = parseRole(roleName);
        return ResponseEntity.ok(authService.getUsersByRole(role));
    }

    @Operation(summary = "Get an auth user by id (ADMIN only)")
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getById(id));
    }

    /**
     * Fallback lookup used by the owner UI when User Service has no profile
     * row for a tenant (legacy registrations, Feign hiccup, etc.). Returns
     * just the bare-bones identity (userName, email, role) so we can render
     * SOMETHING useful instead of "Couldn't load tenant profile". Same path
     * as /users/{id} but accessible to ADMIN and OWNER.
     */
    @Operation(summary = "Bare-bones auth-user lookup by id (ADMIN + OWNER)")
    @GetMapping("/users/lookup/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<AuthUserResponse> lookupById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getById(id));
    }

    /**
     * Audit H3: per-user "tokens issued before this instant are dead"
     * timestamp. Read by api-gateway's JWT validator (with a Caffeine
     * cache) to enforce immediate logout — any access JWT whose
     * {@code iat} is older than this value is rejected.
     *
     * <p>Returns -1 if the user has never logged out (never bumped the
     * watermark) so the gateway can skip the {@code iat} check
     * cheaply.
     *
     * <p>Open to authenticated callers — every JWT-bearing request
     * needs this; rate-limited by the gateway's per-route bucket
     * (audit H1) so it can't be used to enumerate user ids.
     */
    @Operation(summary = "Per-user tokens-revoked-before watermark (internal — used by gateway JWT validator)")
    @GetMapping("/internal/tokens-revoked-before/{userId}")
    public ResponseEntity<java.util.Map<String, Long>> tokensRevokedBefore(@PathVariable Long userId) {
        java.time.Instant when = authService.tokensRevokedBefore(userId);
        return ResponseEntity.ok(java.util.Map.of(
                "userId",  userId,
                "epochMs", when == null ? -1L : when.toEpochMilli()));
    }

    /**
     * Internal: owner-initiated tenant → maintainer promotion. Called by
     * property-service's {@code SocietyService.promoteTenantToMaintainer}
     * after it has verified that:
     * <ol>
     *   <li>the caller is the building's owner,</li>
     *   <li>the targeted authUserId is currently a tenant of one of the
     *       building's flats.</li>
     * </ol>
     *
     * <p>The route is gated by the gateway HMAC (the {@code /auth/internal/**}
     * permitAll at the Spring layer is intentional — see SecurityConfig).
     * Direct hits without the HMAC header are blocked by
     * {@link com.spa.home_rental_application.auth_commons.GatewayAuthFilter}.
     */
    @Operation(summary = "Promote a tenant to MAINTAINER + reset their password (internal)")
    @PostMapping(value = "/internal/users/{authUserId}/promote-to-maintainer",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthUserResponse> promoteToMaintainer(
            @PathVariable Long authUserId,
            @Valid @RequestBody PromoteToMaintainerRequest req) {
        log.info("POST /auth/internal/users/{}/promote-to-maintainer", authUserId);
        return ResponseEntity.ok(authService.promoteToMaintainer(authUserId, req.newPassword()));
    }

    /**
     * Bump an existing user's role to MAINTAINER without touching the
     * password. Used by property-service when an owner approves a
     * self-service membership claim — the claimant chose their own
     * password at signup, so the dual-credential dance from
     * {@link #promoteToMaintainer} would invalidate it for no benefit.
     *
     * <p>Same gateway-HMAC gating as the sibling internal endpoint.
     * Direct hits without the X-Internal-Auth-Sig header are blocked
     * by GatewayAuthFilter.
     */
    @Operation(summary = "Grant MAINTAINER role to an existing user (internal, no password change)")
    @PostMapping("/internal/users/{authUserId}/grant-maintainer-role")
    public ResponseEntity<AuthUserResponse> grantMaintainerRole(@PathVariable Long authUserId) {
        log.info("POST /auth/internal/users/{}/grant-maintainer-role", authUserId);
        return ResponseEntity.ok(authService.grantMaintainerRole(authUserId));
    }

    /**
     * Sibling of {@link #grantMaintainerRole} for society-only
     * residents. Called by property-service when an owner approves a
     * RESIDENT membership claim. Adds MAINTAINEE to the user's role
     * set (and promotes their primary role from TENANT to MAINTAINEE
     * for pure-maintainee signups).
     *
     * <p>Same X-Internal-Auth-Sig gating as the maintainer variant.
     */
    @Operation(summary = "Grant MAINTAINEE role to an existing user (internal, no password change)")
    @PostMapping("/internal/users/{authUserId}/grant-maintainee-role")
    public ResponseEntity<AuthUserResponse> grantMaintaineeRole(@PathVariable Long authUserId) {
        log.info("POST /auth/internal/users/{}/grant-maintainee-role", authUserId);
        return ResponseEntity.ok(authService.grantMaintaineeRole(authUserId));
    }


    private static Roles parseRole(String input) {
        try {
            return Roles.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role: " + input);
        }
    }


    /**
     * Audit M2: trust X-Forwarded-For ONLY when the request actually
     * came through the gateway (X-Internal-Auth-Sig present means
     * the auth-commons GatewayAuthFilter accepted it). Direct hits
     * to auth-service can supply any XFF they want — we'd be a
     * fool to log it as "the user's IP". For direct hits, use the
     * actual remote address.
     *
     * <p>Bonus: when XFF is trusted, take the LAST hop in the chain
     * instead of the first. The first entry is the
     * client-supplied claim (forgeable upstream of our trusted
     * proxy chain); the last is what the trusted proxy ACTUALLY saw.
     * This is the inverse of what intuition suggests but it's what
     * RFC 7239 + every modern WAF recommends.
     */
    private static String clientIp(HttpServletRequest req) {
        String internalAuthSig = req.getHeader("X-Internal-Auth-Sig");
        String xff = req.getHeader("X-Forwarded-For");
        boolean fromGateway = internalAuthSig != null && !internalAuthSig.isBlank();
        if (fromGateway && xff != null && !xff.isBlank()) {
            // Trust XFF — pick the leftmost (original client) but only
            // because the gateway-fronted XFF is single-hop in our
            // deploy. If we later sit behind a CDN, switch to taking
            // the entry just BEFORE the trusted-proxy IP.
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
