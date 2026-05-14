package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.BankAccountRequestDto;
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
}
