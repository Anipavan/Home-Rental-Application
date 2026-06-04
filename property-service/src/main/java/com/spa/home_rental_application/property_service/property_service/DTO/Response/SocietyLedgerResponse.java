package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import com.spa.home_rental_application.property_service.property_service.enums.ExpenseCategory;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Combined "what does the ledger look like right now" payload — the
 * one screen-load the dashboards (owner, tenant, public) all hit.
 * Includes monthly + lifetime totals + the expense list so the UI
 * renders the whole month-tab in a single round trip.
 *
 * <p>The {@code societyDisplayName} is included on every response
 * (public + private) because it's not sensitive. The
 * {@code publicViewUrl} is intentionally absent from the public
 * response — only the owner / maintainer sees it so they can copy
 * the shareable link to a WhatsApp group.
 */
@Builder
public record SocietyLedgerResponse(
        String buildingId,
        String societyDisplayName,
        String month,                              // YYYY-MM the slice was for
        BigDecimal expensesThisMonth,
        BigDecimal collectedThisMonth,             // SUM(amount_paid) where status=PAID + forMonth=month
        BigDecimal collectedThisYear,              // SUM(amount_paid) across this calendar year
        BigDecimal outstandingThisMonth,           // SUM(amount_due) where status in (DUE,OVERDUE)
        BigDecimal balanceLifetime,                // collectedLifetime - expensesLifetime
        BigDecimal expensesLifetime,
        BigDecimal collectedLifetime,
        Map<ExpenseCategory, BigDecimal> byCategory,
        List<MaintenanceExpenseResponse> expenses,

        /* ─── Per-flat bills (V5 enrichment) ───
         * One entry per flat in the building for this month, with
         * category breakdown + status + totals. Surfaced on the
         * public read-only ledger so residents can see at a glance
         * which flats have settled and which haven't.
         *
         * <p>NO tenant names or phone numbers — the public URL is
         * link-credential, and we don't map flat numbers to people
         * on it. Residents who need that mapping already know it.
         */
        List<PublicFlatBillResponse> flatBills,

        /* ─── Maintainer contact ───
         * Resolved from user-service at response time. Surfaced on
         * the public ledger so a resident can call / message the
         * person responsible for the books without needing a login.
         * Nullable when user-service is unreachable (we render an
         * empty card in that case rather than 500-ing).
         */
        String maintainerName,
        String maintainerPhone,
        String maintainerEmail
) {
}
