package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AddExpenseRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.InitiateSocietyChargePaymentRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.PromoteTenantToMaintainerRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.SetupSocietyRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.UpsertFlatCollectionRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.EligibleMaintainerResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatMaintenanceRowResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MaintenanceExpenseResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.PromoteTenantResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyChargeLineItemResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyChargePaymentInitiatedResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyConfigResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyLedgerResponse;
import com.spa.home_rental_application.property_service.property_service.service.SocietyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Society / common-area maintenance ledger endpoints.
 *
 * <p>Public path: {@code GET /society/public/{token}/ledger} is the
 * only no-auth endpoint — wired through the gateway's
 * {@code app.gateway.public-paths} whitelist. Every other route
 * runs through the standard JWT auth + role check.
 *
 * <p>Mutation endpoints enforce ownership / maintainer authority
 * inside the service layer via
 * {@code CallerSecurity.requireOwnerOrAdmin(...)} so we don't
 * scatter security logic across the controller.
 */
@RestController
@RequestMapping(value = "/society", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Society", description = "Building common-area maintenance ledger")
public class SocietyController {

    private final SocietyService service;

    public SocietyController(SocietyService service) {
        this.service = service;
    }

    // ── Config ────────────────────────────────────────────────────

    @Operation(summary = "Owner-side: enable society / common-area maintenance for a building. One-time per building.")
    @PostMapping(value = "/{buildingId}/setup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SocietyConfigResponse> setup(
            @PathVariable String buildingId,
            @Valid @RequestBody SetupSocietyRequest body) {
        log.info("POST /society/{}/setup default={} maintainer={}",
                buildingId, body.defaultPerFlatAmount(), body.maintainerUserId());
        return ResponseEntity.ok(service.setupSociety(buildingId, body));
    }

    @Operation(summary = "Get the society config for a building.")
    @GetMapping("/{buildingId}")
    public ResponseEntity<SocietyConfigResponse> get(@PathVariable String buildingId) {
        return ResponseEntity.ok(service.getConfig(buildingId));
    }

    @Operation(summary = "Owner / maintainer: update config (display name, due day, default amount, or reassign maintainer).")
    @PutMapping(value = "/{buildingId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SocietyConfigResponse> update(
            @PathVariable String buildingId,
            @Valid @RequestBody SetupSocietyRequest body) {
        return ResponseEntity.ok(service.updateConfig(buildingId, body));
    }

    @Operation(summary = "Owner: rotate the public read-only ledger token.")
    @PostMapping("/{buildingId}/regenerate-token")
    public ResponseEntity<SocietyConfigResponse> regenerateToken(
            @PathVariable String buildingId) {
        log.info("POST /society/{}/regenerate-token", buildingId);
        return ResponseEntity.ok(service.regeneratePublicToken(buildingId));
    }

    @Operation(summary = "Tenant: report that the society's UPI ID isn't working. "
            + "Flags the config so the maintainer notices; auto-clears on next bank edit.")
    @PostMapping("/{buildingId}/report-bank-issue")
    public ResponseEntity<SocietyConfigResponse> reportBankIssue(
            @PathVariable String buildingId) {
        log.info("POST /society/{}/report-bank-issue", buildingId);
        return ResponseEntity.ok(service.reportBankIssue(buildingId));
    }

    @Operation(summary = "Owner/maintainer: manually clear the bank-config flag.")
    @PostMapping("/{buildingId}/report-bank-issue/clear")
    public ResponseEntity<SocietyConfigResponse> clearBankIssueFlag(
            @PathVariable String buildingId) {
        log.info("POST /society/{}/report-bank-issue/clear", buildingId);
        return ResponseEntity.ok(service.clearBankIssueFlag(buildingId));
    }

    /* ── Announcements (V17) ─────────────────────────────────────── */

    @Operation(summary = "Owner/maintainer: post a new building announcement.")
    @PostMapping("/{buildingId}/announcements")
    public ResponseEntity<com.spa.home_rental_application.property_service.property_service.DTO.Response.AnnouncementResponse>
        createAnnouncement(@PathVariable String buildingId,
                           @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
                           com.spa.home_rental_application.property_service.property_service.DTO.Request.AnnouncementRequest req) {
        log.info("POST /society/{}/announcements", buildingId);
        return ResponseEntity.ok(service.createAnnouncement(buildingId, req));
    }

    @Operation(summary = "Residents / owner / maintainer: list building announcements, newest first.")
    @org.springframework.web.bind.annotation.GetMapping("/{buildingId}/announcements")
    public ResponseEntity<java.util.List<com.spa.home_rental_application.property_service.property_service.DTO.Response.AnnouncementResponse>>
        listAnnouncements(@PathVariable String buildingId) {
        log.debug("GET /society/{}/announcements", buildingId);
        return ResponseEntity.ok(service.listAnnouncements(buildingId));
    }

    @Operation(summary = "Author / owner / maintainer: delete a building announcement.")
    @org.springframework.web.bind.annotation.DeleteMapping("/{buildingId}/announcements/{announcementId}")
    public ResponseEntity<Void> deleteAnnouncement(
            @PathVariable String buildingId,
            @PathVariable String announcementId) {
        log.info("DELETE /society/{}/announcements/{}", buildingId, announcementId);
        service.deleteAnnouncement(buildingId, announcementId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List all societies the caller manages (as owner or assigned maintainer).")
    @GetMapping("/mine")
    public ResponseEntity<List<SocietyConfigResponse>> mine() {
        return ResponseEntity.ok(service.listMySocieties());
    }

    @Operation(summary = "Get the society for the building the calling tenant currently lives in. Null if no society or no flat.")
    @GetMapping("/my-tenant")
    public ResponseEntity<SocietyConfigResponse> myTenant() {
        return ResponseEntity.ok(service.getMyTenantSociety());
    }

    // ── Expenses ──────────────────────────────────────────────────

    @Operation(summary = "Owner / maintainer: record a common-area expense.")
    @PostMapping(value = "/{buildingId}/expenses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceExpenseResponse> addExpense(
            @PathVariable String buildingId,
            @Valid @RequestBody AddExpenseRequest body) {
        log.info("POST /society/{}/expenses month={} category={} amount={}",
                buildingId, body.expenseMonth(), body.category(), body.amount());
        return ResponseEntity.ok(service.addExpense(buildingId, body));
    }

    @Operation(summary = "Owner / maintainer: edit an existing expense row.")
    @PutMapping(value = "/{buildingId}/expenses/{expenseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceExpenseResponse> updateExpense(
            @PathVariable String buildingId,
            @PathVariable String expenseId,
            @Valid @RequestBody AddExpenseRequest body) {
        return ResponseEntity.ok(service.updateExpense(buildingId, expenseId, body));
    }

    @Operation(summary = "Owner / maintainer: delete an expense row.")
    @DeleteMapping("/{buildingId}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable String buildingId,
            @PathVariable String expenseId) {
        service.deleteExpense(buildingId, expenseId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List expense rows. Owner / maintainer / any tenant of the building / admin.")
    @GetMapping("/{buildingId}/expenses")
    public ResponseEntity<List<MaintenanceExpenseResponse>> listExpenses(
            @PathVariable String buildingId,
            @RequestParam(name = "month", required = false) String month) {
        return ResponseEntity.ok(service.listExpenses(buildingId, month));
    }

    // ── Ledger ────────────────────────────────────────────────────

    @Operation(summary = "Combined ledger view (config + monthly totals + expense list). Owner / maintainer / tenant / admin.")
    @GetMapping("/{buildingId}/ledger")
    public ResponseEntity<SocietyLedgerResponse> ledger(
            @PathVariable String buildingId,
            @RequestParam(name = "month", required = false) String month) {
        return ResponseEntity.ok(service.getLedger(buildingId, month));
    }

    @Operation(summary = "Public read-only ledger via shareable token. No auth required.")
    @GetMapping("/public/{token}/ledger")
    public ResponseEntity<SocietyLedgerResponse> publicLedger(
            @PathVariable String token,
            @RequestParam(name = "month", required = false) String month) {
        log.info("Public ledger view token=*** month={}", month);
        return ResponseEntity.ok(service.getPublicLedger(token, month));
    }

    // ── Maintainer assignment (owner-driven) ──────────────────────

    @Operation(summary = "Owner: list tenants currently in this building's flats — pick one to promote to maintainer.")
    @GetMapping("/{buildingId}/eligible-maintainers")
    public ResponseEntity<List<EligibleMaintainerResponse>> eligibleMaintainers(
            @PathVariable String buildingId) {
        return ResponseEntity.ok(service.listEligibleMaintainers(buildingId));
    }

    @Operation(summary = "Owner: promote an existing tenant to MAINTAINER + reset their password.")
    @PostMapping(value = "/{buildingId}/maintainer/promote-tenant",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PromoteTenantResponse> promoteTenant(
            @PathVariable String buildingId,
            @Valid @RequestBody PromoteTenantToMaintainerRequest body) {
        log.info("POST /society/{}/maintainer/promote-tenant tenant={}",
                buildingId, body.tenantUserId());
        return ResponseEntity.ok(service.promoteTenantToMaintainer(buildingId, body));
    }

    // ── Per-flat maintainer dashboard ────────────────────────────

    @Operation(summary = "Owner / maintainer: per-flat per-month rows for the maintainer dashboard.")
    @GetMapping("/{buildingId}/flats")
    public ResponseEntity<List<FlatMaintenanceRowResponse>> flatsForMonth(
            @PathVariable String buildingId,
            @RequestParam(name = "month", required = false) String month) {
        return ResponseEntity.ok(service.listFlatsForMonth(buildingId, month));
    }

    @Operation(summary = "Maintainer / owner: upsert the (flat, month, category) collection row. Same (flat, month) with a different category creates a NEW row (multi-line charges).")
    @PostMapping(value = "/{buildingId}/flats/{flatId}/collection",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlatMaintenanceRowResponse> upsertFlatCollection(
            @PathVariable String buildingId,
            @PathVariable String flatId,
            @Valid @RequestBody UpsertFlatCollectionRequest body) {
        log.info("POST /society/{}/flats/{}/collection month={} category={} amountDue={} status={}",
                buildingId, flatId, body.forMonth(), body.category(),
                body.amountDue(), body.status());
        return ResponseEntity.ok(service.upsertFlatCollection(buildingId, flatId, body));
    }

    @Operation(summary = "Tenant: every charge against my own flat for the month. Drives the Pay-Now surface on /app/society.")
    @GetMapping("/{buildingId}/my-bills")
    public ResponseEntity<List<FlatMaintenanceRowResponse>> myBills(
            @PathVariable String buildingId,
            @RequestParam(name = "month", required = false) String month) {
        return ResponseEntity.ok(service.listMyBillsForMonth(buildingId, month));
    }

    @Operation(summary = "Internal: list every society-charge row linked to a given paymentId. Used by payment-service to itemise the maintenance-receipt PDF.")
    @GetMapping("/charges/by-payment/{paymentId}")
    public ResponseEntity<List<SocietyChargeLineItemResponse>> chargesByPayment(
            @PathVariable String paymentId) {
        return ResponseEntity.ok(service.getChargesByPaymentId(paymentId));
    }

    @Operation(summary = "Tenant: bridge a set of DUE / OVERDUE charges to the Razorpay rent-pay flow. Returns the new Payment row's id so the FE can navigate to /app/payments/{id}/pay.")
    @PostMapping(value = "/{buildingId}/charges/initiate-payment",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SocietyChargePaymentInitiatedResponse> initiateSocietyChargePayment(
            @PathVariable String buildingId,
            @Valid @RequestBody InitiateSocietyChargePaymentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("POST /society/{}/charges/initiate-payment count={} idempotencyKey={}",
                buildingId, body.collectionIds().size(),
                idempotencyKey == null ? "-" : "set");
        return ResponseEntity.ok(
                service.initiateSocietyChargePayment(buildingId, body, idempotencyKey));
    }
}
