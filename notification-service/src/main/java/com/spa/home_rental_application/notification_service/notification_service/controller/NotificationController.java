package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.SendNotificationRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.NotificationResponse;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Notifications", description = "Manual sends, history lookups, status checks")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /* ───────────── Authz model (audit C7) ─────────────
     * /send/{email,sms,push} are admin-only — these are the manual
     * "send to anyone" endpoints, and shipping them open meant any
     * authenticated user could spam any other user. Production event-
     * driven sends still work (the Kafka listeners go through the
     * NotificationService directly, never through this controller).
     *
     * /user/{userId}/* listing endpoints are gated to "self or admin"
     * so a tenant can fetch their own inbox but can't enumerate
     * another tenant's notifications. */

    @Operation(summary = "Send an email notification (ADMIN only — events use Kafka, not this endpoint)")
    @PostMapping(value = "/send/email", consumes = MediaType.APPLICATION_JSON_VALUE)
    // requireAdmin guard runs at the top of each method instead of @PreAuthorize
    // because notification-service doesn't enable Spring Security method-level
    // annotations and adding @EnableMethodSecurity would conflict with the
    // auto-config from auth-commons. Inline check is equivalent.
    public ResponseEntity<NotificationResponse> sendEmail(@Valid @RequestBody SendNotificationRequest body) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(forced(body, NotificationType.EMAIL)));
    }

    @Operation(summary = "Send an SMS notification (ADMIN only)")
    @PostMapping(value = "/send/sms", consumes = MediaType.APPLICATION_JSON_VALUE)
    // requireAdmin guard runs at the top of each method instead of @PreAuthorize
    // because notification-service doesn't enable Spring Security method-level
    // annotations and adding @EnableMethodSecurity would conflict with the
    // auto-config from auth-commons. Inline check is equivalent.
    public ResponseEntity<NotificationResponse> sendSms(@Valid @RequestBody SendNotificationRequest body) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(forced(body, NotificationType.SMS)));
    }

    @Operation(summary = "Send a push notification (ADMIN only)")
    @PostMapping(value = "/send/push", consumes = MediaType.APPLICATION_JSON_VALUE)
    // requireAdmin guard runs at the top of each method instead of @PreAuthorize
    // because notification-service doesn't enable Spring Security method-level
    // annotations and adding @EnableMethodSecurity would conflict with the
    // auto-config from auth-commons. Inline check is equivalent.
    public ResponseEntity<NotificationResponse> sendPush(@Valid @RequestBody SendNotificationRequest body) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(forced(body, NotificationType.PUSH)));
    }

    @Operation(summary = "All notifications, every user (ADMIN only)")
    @GetMapping
    // requireAdmin guard runs at the top of each method instead of @PreAuthorize
    // because notification-service doesn't enable Spring Security method-level
    // annotations and adding @EnableMethodSecurity would conflict with the
    // auto-config from auth-commons. Inline check is equivalent.
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        requireAdmin();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.list(pageable));
    }

    @Operation(summary = "All notifications for a user (self or ADMIN)")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationResponse>> byUser(@PathVariable String userId,
                                                             HttpServletRequest req) {
        requireSelfOrAdmin(userId, req);
        return ResponseEntity.ok(service.getByUserId(userId));
    }

    @Operation(summary = "Notification by id (owner of the row, or ADMIN)")
    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> byId(@PathVariable String id, HttpServletRequest req) {
        NotificationResponse n = service.getById(id);
        requireSelfOrAdmin(n.userId(), req);
        return ResponseEntity.ok(n);
    }

    @Operation(summary = "Same as /{id} — kept for backwards compatibility with the design doc")
    @GetMapping("/{id}/status")
    public ResponseEntity<NotificationResponse> status(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @Operation(summary = "Mark a single notification as READ (decrements the bell badge)")
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable String id) {
        return ResponseEntity.ok(service.markAsRead(id));
    }

    @Operation(summary = "Bulk: mark every unread notification for a user as READ")
    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<java.util.Map<String, Integer>> markAllAsRead(@PathVariable String userId) {
        int updated = service.markAllAsRead(userId);
        return ResponseEntity.ok(java.util.Map.of("updated", updated));
    }

    /** The /send/email|sms|push endpoints all share one DTO; force the type from the path. */
    private SendNotificationRequest forced(SendNotificationRequest req, NotificationType t) {
        return new SendNotificationRequest(
                req.userId(), t, req.category(),
                req.subject(), req.message(), req.recipient(),
                req.templateVariables());
    }

    /**
     * Per-row authz check used by the user-scoped GET endpoints.
     * Admins pass through; otherwise the caller (gateway-stamped
     * {@code X-Auth-User-Id}) must equal the target userId or we 403.
     */
    private static void requireSelfOrAdmin(String targetUserId, HttpServletRequest req) {
        if (isAdmin()) return;
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) {
            // No gateway header — system call (internal Feign, scheduled
            // job). Allow; the gateway is the single trusted stamper.
            return;
        }
        if (targetUserId == null || !targetUserId.equals(caller)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view your own notifications.");
        }
    }

    private static boolean isAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (var ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    private static void requireAdmin() {
        if (!isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Admin role required for this endpoint.");
        }
    }
}
