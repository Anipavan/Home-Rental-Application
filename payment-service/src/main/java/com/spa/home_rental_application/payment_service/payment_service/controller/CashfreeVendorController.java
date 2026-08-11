package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.entities.CashfreeVendor;
import com.spa.home_rental_application.payment_service.payment_service.security.CallerSecurity;
import com.spa.home_rental_application.payment_service.payment_service.service.CashfreeVendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Admin surface for the Cashfree Easy Split vendor list.
 * <p>Read-mostly. The only mutation is a "re-register this failed
 * vendor" retry button; happy-path registration is fully event-driven.
 */
@RestController
@RequestMapping(value = "/payments/vendors", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Cashfree Vendors",
        description = "Admin view of owner vendor onboarding + manual retry")
public class CashfreeVendorController {

    private final CashfreeVendorService service;

    public CashfreeVendorController(CashfreeVendorService service) {
        this.service = service;
    }

    @Operation(summary = "List every Cashfree vendor row (admin only)")
    @GetMapping
    public ResponseEntity<List<VendorRow>> list() {
        requireAdmin();
        return ResponseEntity.ok(service.listAll().stream().map(VendorRow::from).toList());
    }

    @Operation(summary = "Fetch a single owner's vendor row")
    @GetMapping("/{userId}")
    public ResponseEntity<VendorRow> get(@PathVariable String userId) {
        requireAdmin();
        return service.getForUser(userId)
                .map(VendorRow::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No vendor row for userId=" + userId));
    }

    @Operation(summary = "Force a re-registration attempt (resets the retry counter)")
    @PostMapping("/{userId}/re-register")
    public ResponseEntity<VendorRow> reRegister(@PathVariable String userId) {
        requireAdmin();
        log.info("Admin re-register vendor userId={} actor={}",
                userId, CallerSecurity.getCurrentAuthUserId().orElse("?"));
        return service.reRegister(userId)
                .map(VendorRow::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No vendor row for userId=" + userId));
    }

    private static void requireAdmin() {
        if (!CallerSecurity.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }

    /** Response shape — hides the internal DB id and the accumulator columns. */
    public record VendorRow(
            String userId,
            String cashfreeVendorId,
            String status,
            String bankAccountLast4,
            String bankIfsc,
            String bankAccountHolder,
            String failureReason,
            Integer attemptCount,
            Instant lastAttemptedAt,
            Instant activatedAt,
            Instant updatedAt
    ) {
        public static VendorRow from(CashfreeVendor v) {
            return new VendorRow(
                    v.getUserId(),
                    v.getCashfreeVendorId(),
                    v.getStatus() == null ? null : v.getStatus().name(),
                    v.getBankAccountLast4(),
                    v.getBankIfsc(),
                    v.getBankAccountHolder(),
                    v.getFailureReason(),
                    v.getAttemptCount(),
                    v.getLastAttemptedAt(),
                    v.getActivatedAt(),
                    v.getUpdatedAt()
            );
        }
    }
}
