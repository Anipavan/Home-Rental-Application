package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AddExpenseRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.SetupSocietyRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MaintenanceExpenseResponse;
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

    /** All societies the caller manages — owner or assigned maintainer.
     *  Powers the /owner/society overview list. */
    List<SocietyConfigResponse> listMySocieties();

    /** The society for the building the calling TENANT currently lives in.
     *  Returns null when the tenant isn't assigned to a flat OR the
     *  building has no society config yet. */
    SocietyConfigResponse getMyTenantSociety();

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
}
