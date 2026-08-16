package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.entities.SocietyCashfreeVendor;
import com.spa.home_rental_application.payment_service.payment_service.security.CallerSecurity;
import com.spa.home_rental_application.payment_service.payment_service.service.SocietyCashfreeVendorService;
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
 * Admin + tenant-payout-check surface for the SOCIETY-vendor list.
 * Sibling of {@link CashfreeVendorController} — same shape, keyed on
 * {@code buildingId} instead of {@code userId}. Powers the maintenance
 * pay flow's payout-ready gate on the tenant side and the vendor-status
 * dashboard on the admin side.
 */
@RestController
@RequestMapping(value = "/payments/vendors/society", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Society Cashfree Vendors",
        description = "Society-scoped vendor onboarding + tenant payout-ready check")
public class SocietyCashfreeVendorController {

    private final SocietyCashfreeVendorService service;

    public SocietyCashfreeVendorController(SocietyCashfreeVendorService service) {
        this.service = service;
    }

    @Operation(summary = "List every society Cashfree vendor row (admin only)")
    @GetMapping
    public ResponseEntity<List<VendorRow>> list() {
        requireAdmin();
        return ResponseEntity.ok(service.listAll().stream().map(VendorRow::from).toList());
    }

    @Operation(summary = "Fetch a single building's society-vendor row")
    @GetMapping("/{buildingId}")
    public ResponseEntity<VendorRow> get(@PathVariable String buildingId) {
        requireAdmin();
        return service.getForBuilding(buildingId)
                .map(VendorRow::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No society-vendor row for buildingId=" + buildingId));
    }

    @Operation(summary = "Force a re-registration attempt for the society vendor")
    @PostMapping("/{buildingId}/re-register")
    public ResponseEntity<VendorRow> reRegister(@PathVariable String buildingId) {
        requireAdmin();
        log.info("Admin re-register society vendor buildingId={} actor={}",
                buildingId, CallerSecurity.getCurrentAuthUserId().orElse("?"));
        return service.reRegister(buildingId)
                .map(VendorRow::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No society-vendor row for buildingId=" + buildingId));
    }

    /**
     * Public-ish: exposes only the boolean payout-ready state for the
     * society, so the tenant Society page can decide whether to enable
     * the maintenance Pay buttons or fall back to the direct-UPI QR.
     * Never 404s — an un-registered building reads as "not ready".
     */
    @Operation(summary = "Public: is this society ready to receive maintenance payments?")
    @GetMapping("/{buildingId}/payout-ready")
    public ResponseEntity<java.util.Map<String, Boolean>> payoutReady(@PathVariable String buildingId) {
        boolean ready = service.getForBuilding(buildingId)
                .map(v -> v.getStatus() != null && v.getStatus().isPayoutReady())
                .orElse(false);
        return ResponseEntity.ok(java.util.Map.of("ready", ready));
    }

    private static void requireAdmin() {
        if (!CallerSecurity.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }

    public record VendorRow(
            String buildingId,
            String societyConfigId,
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
        public static VendorRow from(SocietyCashfreeVendor v) {
            return new VendorRow(
                    v.getBuildingId(),
                    v.getSocietyConfigId(),
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
