package com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response;

import java.math.BigDecimal;

public record ComparisonResponse(
        String label,           // e.g. "this-month-vs-last-month"
        BigDecimal currentValue,
        BigDecimal previousValue,
        BigDecimal absoluteDelta,
        Double     percentDelta
) {}
