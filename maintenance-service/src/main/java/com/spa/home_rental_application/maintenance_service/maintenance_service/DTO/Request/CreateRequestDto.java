package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /maintenance/requests. */
public record CreateRequestDto(
        @NotBlank(message = "tenantId is mandatory") String tenantId,
        @NotBlank(message = "flatId is mandatory")   String flatId,
        String  ownerId,
        @NotNull(message = "category is mandatory")  Category category,
        @NotBlank(message = "title is mandatory") @Size(max = 200) String title,
        @NotBlank(message = "description is mandatory") @Size(max = 4000) String description,
        @NotNull(message = "priority is mandatory")  Priority priority
) {}
