package com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response;

import java.time.LocalDate;

public record OccupancyResponse(
        String buildingId,
        LocalDate statDate,
        int totalFlats,
        int occupiedFlats,
        int vacantFlats,
        double occupancyRate
) {}
