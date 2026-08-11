package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.DTO.CommissionDtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin-configurable commission engine.
 *
 * <p>Rules live in the {@code commission_rules} table: one global-default
 * row plus zero-or-more per-owner overrides. At payment-initiate time the
 * split-payment code calls {@link #computePlatformFee} to figure out how
 * much of the rent goes to the platform (kept) vs the owner (routed
 * through Cashfree Easy Split at their end).
 *
 * <p>The admin surface is a single {@code /admin/commission} page that
 * reads and writes these rules. All admin writes bump
 * {@code updated_by_admin_id} for the audit trail.
 */
public interface CommissionService {

    /**
     * Compute the platform's cut of a payment.
     *
     * @param ownerId      the rent's recipient — drives the two-tier
     *                     lookup (owner override first, global fallback)
     * @param totalAmount  the amount the tenant is paying (base + late fee)
     * @return the commission amount, in rupees, rounded to two decimal
     *         places (HALF_UP). Zero when no rule applies OR the applicable
     *         rule is 0 bps.
     */
    BigDecimal computePlatformFee(String ownerId, BigDecimal totalAmount);

    /** The current global-default rule. Never null after V6 seed runs. */
    CommissionDtos.RuleResponse getGlobal();

    /**
     * Set the global-default rate. Idempotent — updates the seeded row
     * in place rather than inserting a new one.
     */
    CommissionDtos.RuleResponse setGlobal(CommissionDtos.UpsertRuleRequest req, String actorAdminId);

    /**
     * List every per-owner override, ordered by owner id for stable
     * table rendering.
     */
    List<CommissionDtos.RuleResponse> listOverrides();

    /**
     * Upsert a per-owner override. Idempotent — repeat calls update
     * the same row rather than inserting duplicates.
     */
    CommissionDtos.RuleResponse upsertOverride(String ownerId,
                                                CommissionDtos.UpsertRuleRequest req,
                                                String actorAdminId);

    /**
     * Delete a per-owner override. Reverts that owner back to the
     * global default at the next payment. Idempotent — deleting a
     * non-existent override is a no-op.
     */
    void deleteOverride(String ownerId, String actorAdminId);

    /**
     * Dry-run compute for the admin dashboard's live preview strip.
     * Uses the same {@link #computePlatformFee} rules — just formats
     * the answer for display.
     */
    CommissionDtos.PreviewResponse preview(String ownerId, BigDecimal totalAmount);
}
