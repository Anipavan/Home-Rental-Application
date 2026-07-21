package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /society/{buildingId}/announcements}. Both
 * fields are required; buildingId comes from the path, authorUserId
 * from the JWT — never from the wire.
 */
public record AnnouncementRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be under 200 characters")
        String title,

        @NotBlank(message = "Body is required")
        @Size(max = 4000, message = "Body must be under 4000 characters")
        String body
) {
}
