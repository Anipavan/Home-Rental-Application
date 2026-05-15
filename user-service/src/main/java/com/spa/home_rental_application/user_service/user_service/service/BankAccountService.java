package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.BankAccountRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.BankAccountPayoutDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.BankAccountResponseDto;

import java.util.Optional;

public interface BankAccountService {

    /**
     * Look up the bank account on file for a user. Empty Optional when
     * the user hasn't saved one yet — the SPA renders the empty-state
     * "Add bank details" call-to-action in that case.
     */
    Optional<BankAccountResponseDto> getByUserId(String userId);

    /**
     * Upsert. Present row → update in place (preserves id + createdAt);
     * absent → insert a fresh row. The unique constraint on
     * {@code user_id} guarantees a user never accumulates duplicate
     * rows even under concurrent saves.
     */
    BankAccountResponseDto save(String userId, BankAccountRequestDto body);

    /**
     * Remove the user's saved bank account. Idempotent — no-op when
     * the user had none on file (so the SPA can "remove" without a
     * preflight check).
     */
    void delete(String userId);

    /**
     * Payable subset of a user's bank account — what a payer needs to
     * actually pay them, with the full account number masked. Powers
     * the tenant-pays-rent-direct-to-owner flow: the tenant resolves
     * the owner's payout details, then either scans a UPI QR built
     * from {@code upiId} or NEFT/IMPSes to the masked-but-known
     * account.
     *
     * <p>Returns empty when the user hasn't saved a bank account yet
     * — the caller (payment-service) translates that to a clear
     * "owner hasn't set up payment details" error for the tenant.
     */
    Optional<BankAccountPayoutDto> getPayoutByUserId(String userId);
}
