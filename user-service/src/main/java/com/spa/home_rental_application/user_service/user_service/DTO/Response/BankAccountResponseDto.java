package com.spa.home_rental_application.user_service.user_service.DTO.Response;

import java.time.LocalDateTime;

/**
 * Bank account payload returned to the SPA. The full account number
 * is NEVER sent over the wire — {@code accountNumberMasked} renders
 * as {@code XXXX XXXX 1234}, only the last 4 digits being meaningful
 * to the user (matches Amazon / Razorpay / most fintech apps).
 */
public record BankAccountResponseDto(
        String id,
        String userId,
        String accountHolderName,
        String bankName,
        /** Masked form — backend strips the first 5-14 digits before sending. */
        String accountNumberMasked,
        String ifscCode,
        String branch,
        String accountType,
        String upiId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
