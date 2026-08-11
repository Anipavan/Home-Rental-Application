package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.CommissionDtos;
import com.spa.home_rental_application.payment_service.payment_service.security.CallerSecurity;
import com.spa.home_rental_application.payment_service.payment_service.service.CommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * Admin-only surface for editing the commission engine.
 *
 * <p>Follows the existing payment-service pattern of gating admin
 * endpoints with {@link CallerSecurity#isAdmin()} rather than
 * {@code @PreAuthorize} — this codebase doesn't have method-security
 * enabled service-wide, so @PreAuthorize annotations get silently
 * ignored. The gateway populates the caller's authorities via
 * {@code X-Auth-Roles}; CallerSecurity reads them off the
 * SecurityContext at check time.
 */
@RestController
@RequestMapping(value = "/payments/commission", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Commission", description = "Admin-configurable platform commission engine")
public class CommissionController {

    private final CommissionService service;

    public CommissionController(CommissionService service) {
        this.service = service;
    }

    /* ---------- Global default ---------- */

    @Operation(summary = "Read the global default commission rule")
    @GetMapping("/global")
    public ResponseEntity<CommissionDtos.RuleResponse> getGlobal() {
        requireAdmin();
        return ResponseEntity.ok(service.getGlobal());
    }

    @Operation(summary = "Set the global default commission rate (percentage)")
    @PutMapping(value = "/global", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommissionDtos.RuleResponse> setGlobal(
            @Valid @RequestBody CommissionDtos.UpsertRuleRequest req) {
        requireAdmin();
        String actor = CallerSecurity.getCurrentAuthUserId().orElse(null);
        log.info("PUT /payments/commission/global ratePercent={} actor={}",
                req.ratePercent(), actor);
        return ResponseEntity.ok(service.setGlobal(req, actor));
    }

    /* ---------- Per-owner overrides ---------- */

    @Operation(summary = "List every per-owner commission override")
    @GetMapping("/overrides")
    public ResponseEntity<List<CommissionDtos.RuleResponse>> listOverrides() {
        requireAdmin();
        return ResponseEntity.ok(service.listOverrides());
    }

    @Operation(summary = "Upsert a per-owner commission override")
    @PutMapping(value = "/overrides/{ownerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommissionDtos.RuleResponse> upsertOverride(
            @PathVariable String ownerId,
            @Valid @RequestBody CommissionDtos.UpsertRuleRequest req) {
        requireAdmin();
        String actor = CallerSecurity.getCurrentAuthUserId().orElse(null);
        log.info("PUT /payments/commission/overrides/{} ratePercent={} actor={}",
                ownerId, req.ratePercent(), actor);
        return ResponseEntity.ok(service.upsertOverride(ownerId, req, actor));
    }

    @Operation(summary = "Remove a per-owner override (reverts owner to global default)")
    @DeleteMapping("/overrides/{ownerId}")
    public ResponseEntity<Void> deleteOverride(@PathVariable String ownerId) {
        requireAdmin();
        String actor = CallerSecurity.getCurrentAuthUserId().orElse(null);
        log.info("DELETE /payments/commission/overrides/{} actor={}", ownerId, actor);
        service.deleteOverride(ownerId, actor);
        return ResponseEntity.noContent().build();
    }

    /* ---------- Preview ---------- */

    @Operation(summary = "Dry-run compute — shows the fee that would apply for the given amount + owner")
    @GetMapping("/preview")
    public ResponseEntity<CommissionDtos.PreviewResponse> preview(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "ownerId", required = false) String ownerId) {
        requireAdmin();
        return ResponseEntity.ok(service.preview(ownerId, amount));
    }

    /**
     * Gate every endpoint on ADMIN authority. Non-admins get a 403 with
     * no route-specific detail so the endpoint set isn't enumerable via
     * error messages.
     */
    private static void requireAdmin() {
        if (!CallerSecurity.isAdmin()) {
            throw new ResponseStatusException(FORBIDDEN, "Admin only");
        }
    }
}
