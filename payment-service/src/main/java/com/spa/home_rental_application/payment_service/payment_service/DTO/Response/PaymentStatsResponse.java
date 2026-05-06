package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import java.math.BigDecimal;

public record PaymentStatsResponse(
        long totalPayments,
        long paidCount,
        long pendingCount,
        long overdueCount,
        long failedCount,
        BigDecimal totalAmountPaid,
        BigDecimal totalAmountPending,
        BigDecimal totalLateFeeCollected
) {}
