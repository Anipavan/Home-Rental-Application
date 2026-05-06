package com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response;

import java.math.BigDecimal;
import java.time.Instant;

public record RevenueResponse(
        String ownerId,
        int year,
        int month,
        BigDecimal totalRevenue,
        BigDecimal totalPaid,
        BigDecimal totalPending,
        BigDecimal totalOverdue,
        long paymentCount,
        Instant generatedAt
) {}
