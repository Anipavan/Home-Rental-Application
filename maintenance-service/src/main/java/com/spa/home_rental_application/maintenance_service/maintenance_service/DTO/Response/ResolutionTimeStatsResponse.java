package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response;

public record ResolutionTimeStatsResponse(
        long sampleSize,
        double averageMinutes,
        long minMinutes,
        long maxMinutes
) {}
