package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import jakarta.validation.constraints.Size;

/** PATCH-style body for PUT /maintenance/requests/{id}. All fields optional. */
public record UpdateRequestDto(
        Category category,
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        Priority priority
) {}
