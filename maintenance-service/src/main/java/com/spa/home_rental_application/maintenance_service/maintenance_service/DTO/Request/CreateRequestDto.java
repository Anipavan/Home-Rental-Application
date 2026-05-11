package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.ComplaintCategory;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /maintenance/requests}.
 *
 * <p>The same endpoint creates BOTH maintenance tickets and complaints
 * — the {@link #kind} discriminator picks one. Validation is cross-
 * field at the service layer:
 *
 * <ul>
 *   <li>{@code kind = MAINTENANCE} (default) → {@link #category} required,
 *       {@link #complaintCategory} ignored.</li>
 *   <li>{@code kind = COMPLAINT} → {@link #complaintCategory} required,
 *       {@link #category} ignored.</li>
 * </ul>
 *
 * <p>This shape keeps the existing maintenance clients backwards-compatible:
 * they continue to send the original fields and never need to know
 * complaints exist.
 */
public record CreateRequestDto(
        @NotBlank(message = "tenantId is mandatory") String tenantId,
        @NotBlank(message = "flatId is mandatory")   String flatId,
        String  ownerId,
        /** Defaults to MAINTENANCE when omitted — keeps legacy clients working. */
        Kind kind,
        /** Required when kind = MAINTENANCE. */
        Category category,
        /** Required when kind = COMPLAINT. */
        ComplaintCategory complaintCategory,
        @NotBlank(message = "title is mandatory") @Size(max = 200) String title,
        @NotBlank(message = "description is mandatory") @Size(max = 4000) String description,
        @NotNull(message = "priority is mandatory")  Priority priority
) {}
