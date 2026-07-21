package com.spa.home_rental_application.property_service.property_service.service;

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

import java.util.List;

/**
 * The society / common-area maintenance ledger API surface.
 *
 * <p>Authorization is enforced by the impl using
 * {@link com.spa.home_rental_application.property_service.property_service.security.CallerSecurity}:
 * <ul>
 *   <li>Setup / mutation operations require the building owner or admin.</li>
 *   <li>Maintainer who is NOT the owner can mutate after the owner
 *       has assigned them (looked up via SocietyConfig.maintainer_user_id).</li>
 *   <li>Read endpoints scoped to authorised viewers: owner, maintainer,
 *       any tenant of a flat in the building, or admin.</li>
 *   <li>Public ledger via token bypasses all auth — the token itself
 *       is the bearer credential.</li>
 * </ul>
 */
public interface SocietyService {

    // ── Config ────────────────────────────────────────────────────
    SocietyConfigResponse setupSociety(String buildingId, SetupSocietyRequest req);

    SocietyConfigResponse getConfig(String buildingId);

    SocietyConfigResponse updateConfig(String buildingId, SetupSocietyRequest req);

    SocietyConfigResponse regeneratePublicToken(String buildingId);

    /**
     * Tenant-invoked report that the society's UPI ID doesn't work.
     * Called from the "This UPI isn't working" button on the direct-
     * UPI pay page. Stamps {@code bank_config_flagged_at} + increments
     * the report counter. Idempotent per session — repeated clicks
     * bump the counter but don't reset the flag timestamp.
     *
     * <p>Auto-cleared when the maintainer next saves fresh UPI /
     * payee-name details via {@link #updateConfig}.
     */
    SocietyConfigResponse reportBankIssue(String buildingId);

    /**
     * Manual clear — maintainer confirms the UPI is fine and the
     * tenant made a mistake. Wipes the flag + resets the counter.
     */
    SocietyConfigResponse clearBankIssueFlag(String buildingId);

    /** All societies the caller manages — owner or assigned maintainer.
     *  Powers the /owner/society overview list. */
    List<SocietyConfigResponse> listMySocieties();

    /** The society for the building the calling TENANT currently lives in.
     *  Returns null when the tenant isn't assigned to a flat OR the
     *  building has no society config yet. */
    SocietyConfigResponse getMyTenantSociety();

    // ── Announcements (V17) ──────────────────────────────────────
    /**
     * Post a new announcement on the building's notice board. Auth:
     * owner / maintainer of the building / admin.
     */
    com.spa.home_rental_application.property_service.property_service.DTO.Response.AnnouncementResponse createAnnouncement(
            String buildingId,
            com.spa.home_rental_application.property_service.property_service.DTO.Request.AnnouncementRequest req);

    /**
     * Newest-first list of announcements for a building. Auth: owner /
     * maintainer / a tenant of a flat in the building / an active
     * society member / admin.
     */
    List<com.spa.home_rental_application.property_service.property_service.DTO.Response.AnnouncementResponse> listAnnouncements(
            String buildingId);

    /**
     * Delete an announcement. Auth: the original author OR the current
     * owner / maintainer / admin — anyone with authority over the
     * building's notice board.
     */
    void deleteAnnouncement(String buildingId, String announcementId);

    // ── Expenses ──────────────────────────────────────────────────
    MaintenanceExpenseResponse addExpense(String buildingId, AddExpenseRequest req);

    MaintenanceExpenseResponse updateExpense(
            String buildingId, String expenseId, AddExpenseRequest req);

    void deleteExpense(String buildingId, String expenseId);

    List<MaintenanceExpenseResponse> listExpenses(String buildingId, String month);

    // ── Ledger (combined view) ────────────────────────────────────
    SocietyLedgerResponse getLedger(String buildingId, String month);

    /** Public read-only view via the shareable token. No auth. */
    SocietyLedgerResponse getPublicLedger(String token, String month);

    // ── Maintainer assignment (owner-driven) ──────────────────────

    /**
     * Tenants currently assigned to any flat in the building. Owner-only;
     * powers the AssignMaintainerDialog dropdown so owners pick from
     * existing residents instead of creating a brand-new account.
     */
    List<EligibleMaintainerResponse> listEligibleMaintainers(String buildingId);

    /**
     * Promote an existing tenant to the building's MAINTAINER role.
     * Owner-only. Side-effects across services:
     * <ul>
     *   <li>auth-service: role flip + password reset + token revocation.</li>
     *   <li>property-service: society's {@code maintainerUserId} updated.</li>
     * </ul>
     * The caller (owner) is expected to share the username + temp
     * password with the maintainer out-of-band (WhatsApp / in-person).
     */
    PromoteTenantResponse promoteTenantToMaintainer(
            String buildingId, PromoteTenantToMaintainerRequest req);

    // ── Per-flat collections (maintainer dashboard) ───────────────

    /**
     * Per-flat per-month rows for the maintainer's dashboard (and the
     * owner's read-only "all flats / dues / collected" view). One row
     * per flat in the building, with the resolved {@code monthAmount}
     * sourced from the maintenance_collection row when present, falling
     * back to the building's {@code defaultPerFlatAmount} otherwise.
     *
     * <p>{@code month} is YYYY-MM. Null/blank falls back to the current
     * calendar month — matches the ledger endpoint's behaviour.
     */
    List<FlatMaintenanceRowResponse> listFlatsForMonth(String buildingId, String month);

    /**
     * Maintainer creates or updates the (flat, month, category)
     * collection row. Inserts on first call for that triple, updates
     * on subsequent calls (composite unique
     * {@code uq_collection_flat_month_category} after V5). Owner is
     * allowed too so they can backfill while the maintainer is being
     * onboarded.
     */
    FlatMaintenanceRowResponse upsertFlatCollection(
            String buildingId, String flatId, UpsertFlatCollectionRequest req);

    /**
     * Every charge against the caller's own flat for the given month.
     * Caller must be a tenant of a flat in the building (the standard
     * society-read check). Returns rows for every category the
     * maintainer has recorded — water bill, maintenance, etc. — each
     * with its own status and amount. Drives the Pay-Now flow on
     * /app/society.
     */
    java.util.List<FlatMaintenanceRowResponse> listMyBillsForMonth(
            String buildingId, String month);

    /**
     * Bridges a set of DUE / OVERDUE society charge rows to the existing
     * Razorpay-backed rent-pay flow. Called by the tenant when they hit
     * "Pay all via Razorpay" on /app/society/pay-all.
     *
     * <p>Mechanics:
     * <ol>
     *   <li>Validates every collectionId belongs to a flat occupied by
     *       the caller and is currently DUE / OVERDUE.</li>
     *   <li>Sums their amountDue into one total.</li>
     *   <li>Calls payment-service via Feign to mint a new Payment row
     *       (status=PENDING) for the total against the caller's flat.</li>
     *   <li>Stamps the new paymentId on every collection row so the
     *       {@code PaymentCompletedEvent} consumer can later flip the
     *       linked rows PAID atomically.</li>
     *   <li>Returns the new paymentId so the FE can navigate to the
     *       existing /app/payments/{id}/pay flow — same UPI / Card /
     *       Net-Banking picker as rent.</li>
     * </ol>
     *
     * <p>{@code idempotencyKey} is forwarded as the Idempotency-Key
     * header to payment-service so a fast double-click on "Pay all"
     * creates ONE Razorpay order, not two.
     */
    SocietyChargePaymentInitiatedResponse initiateSocietyChargePayment(
            String buildingId,
            InitiateSocietyChargePaymentRequest req,
            String idempotencyKey);

    /**
     * Kafka-consumer hook for {@code PaymentCompletedEvent}. Marks every
     * {@code maintenance_collection} row whose {@code payment_id}
     * equals the event's paymentId as PAID, with {@code paid_on=event date}
     * and {@code paid_via="RAZORPAY"}. Idempotent — re-delivery hits
     * already-PAID rows and short-circuits without throwing, so the
     * consumer's commit can ack safely on retries.
     */
    void onSocietyChargePaymentCompleted(String paymentId, java.time.LocalDate paidOn);

    /**
     * Every {@code maintenance_collection} row tagged with the given
     * paymentId. Drives the maintenance-receipt PDF — payment-service
     * calls this via Feign to itemise a bulk Pay-all receipt with one
     * line per category (Water bill, Maintenance, Common-area share,
     * etc.) instead of one lumped total.
     *
     * <p>Returns an empty list when paymentId is null or no rows
     * match, which the caller renders as a single fallback "Society
     * charge" line.
     */
    List<SocietyChargeLineItemResponse> getChargesByPaymentId(String paymentId);
}
