package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;

public record CategoryStatsResponse(Category category, long count) {}
