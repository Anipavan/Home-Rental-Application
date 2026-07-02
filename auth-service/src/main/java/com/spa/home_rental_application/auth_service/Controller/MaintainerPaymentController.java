package com.spa.home_rental_application.auth_service.Controller;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.auth_service.Dto.Request.SetPrimaryRoleRequest;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.MaintainerPaymentStatusResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterPendingResponse;
import com.spa.home_rental_application.auth_service.Service.AuthService;
import com.spa.home_rental_application.auth_service.enums.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Logged-in endpoints for the maintainer-payment soft gate.
 * Three routes:
 *
 * <ul>
 *   <li>{@code GET /auth/me/payment-status} — current state-machine
 *       value for the caller. Polled by the maintainer dashboard on
 *       every page load + every 5 minutes while the page is open.</li>
 *   <li>{@code POST /auth/me/payment-skip} — record a "Skip for 4
 *       more days" click. Bumps {@code payment_skip_count} +
 *       stamps {@code payment_last_skip_at = now}. Only valid in
 *       PROMPT state.</li>
 *   <li>{@code POST /auth/me/payment/initiate} — mint a fresh
 *       PENDING Payment + REG_PAY token so the dashboard modal can
 *       route the user into the existing {@code /registration-payment}
 *       page.</li>
 * </ul>
 *
 * <p>Caller identity comes from the {@code X-Auth-User-Id} header
 * stamped by the api-gateway after JWT validation. Direct hits are
 * blocked by {@link GatewayAuthFilter}.
 */
@RestController
@RequestMapping(value = "/auth/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Maintainer Payment",
        description = "Logged-in endpoints for the soft maintainer-payment gate")
public class MaintainerPaymentController {

    private final AuthService authService;

    public MaintainerPaymentController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Current maintainer-payment state for the authenticated user.")
    @GetMapping("/payment-status")
    public ResponseEntity<MaintainerPaymentStatusResponse> getStatus(
            @RequestHeader(GatewayAuthFilter.HDR_UID) Long authUserId) {
        return ResponseEntity.ok(authService.getPaymentStatus(authUserId));
    }

    @Operation(summary = "Record a 'Skip for 4 more days' click. Only valid when status is PROMPT.")
    @PostMapping("/payment-skip")
    public ResponseEntity<MaintainerPaymentStatusResponse> skip(
            @RequestHeader(GatewayAuthFilter.HDR_UID) Long authUserId) {
        log.info("POST /auth/me/payment-skip authUserId={}", authUserId);
        return ResponseEntity.ok(authService.recordPaymentSkip(authUserId));
    }

    @Operation(summary = "Mint a fresh PENDING Payment + REG_PAY token for the dashboard payment modal.")
    @PostMapping("/payment/initiate")
    public ResponseEntity<RegisterPendingResponse> initiate(
            @RequestHeader(GatewayAuthFilter.HDR_UID) Long authUserId) {
        log.info("POST /auth/me/payment/initiate authUserId={}", authUserId);
        return ResponseEntity.ok(authService.initiateOwnPayment(authUserId));
    }

    /**
     * Phase 4 — Welcome-page role picker. Authenticated user swaps
     * their primary role between TENANT and OWNER. Returns a fresh
     * AuthResponse (new JWT + refresh) carrying the updated
     * authorities so the SPA can setSession without re-login.
     */
    @Operation(summary = "Self-service primary role change (TENANT ↔ OWNER only).")
    @PostMapping(value = "/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> setPrimaryRole(
            @RequestHeader(GatewayAuthFilter.HDR_UID) Long authUserId,
            @Valid @RequestBody SetPrimaryRoleRequest req,
            HttpServletRequest servletReq) {
        Roles newRole = Roles.valueOf(req.role());
        log.info("POST /auth/me/role authUserId={} newRole={}", authUserId, newRole);
        String ip = servletReq.getRemoteAddr();
        String ua = servletReq.getHeader("User-Agent");
        return ResponseEntity.ok(authService.setPrimaryRole(authUserId, newRole, ip, ua));
    }
}
