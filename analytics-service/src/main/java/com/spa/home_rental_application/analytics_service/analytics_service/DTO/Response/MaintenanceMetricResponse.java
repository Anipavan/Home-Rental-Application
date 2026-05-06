package com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response;

public record MaintenanceMetricResponse(
        String category,
        long resolvedCount,
        double avgResolutionMinutes
) {}
