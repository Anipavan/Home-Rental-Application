package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.entities.CashfreeVendor;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates Cashfree Easy Split vendor lifecycle for owners.
 *
 * <p>The two Kafka trigger events (bank-account-saved, kyc-verified)
 * both funnel into {@link #tryRegisterIfReady} — the method is
 * idempotent, so replaying either event any number of times just
 * re-checks the prereqs and either advances the state machine or
 * silently no-ops. All state lives in the {@code cashfree_vendors}
 * table.
 */
public interface CashfreeVendorService {

    /**
     * Check both prereqs (bank saved AND kyc verified) for the given
     * user; if both present, attempt Cashfree registration and store
     * the resulting state. Otherwise, save the partial state (PENDING_KYC
     * or PENDING_BANK) so the next trigger can complete it.
     *
     * <p>Never throws — logs and returns the current row so Kafka
     * listeners can drop the record even if downstream services are
     * flaky. The admin dashboard exposes a "Re-register" button for
     * rows stuck in FAILED / REJECTED.
     *
     * @param userId auth user id of the owner
     * @return the current {@link CashfreeVendor} row (present when any
     *         part of the lifecycle has started; empty on complete no-op)
     */
    Optional<CashfreeVendor> tryRegisterIfReady(String userId);

    /** Current vendor row for the owner, if any. */
    Optional<CashfreeVendor> getForUser(String userId);

    /**
     * Admin-triggered retry. Runs the same logic as
     * {@link #tryRegisterIfReady} but resets the attempt counter to
     * zero first so a stuck vendor can move past the retry cap.
     */
    Optional<CashfreeVendor> reRegister(String userId);

    /** All vendors, ordered by last-attempted for the admin dashboard. */
    List<CashfreeVendor> listAll();
}
