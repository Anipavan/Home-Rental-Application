package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Announcement projection returned by both the maintainer's create /
 * list surfaces and the resident's list-on-society-page surface.
 *
 * <p>{@code authorName} is best-effort resolved via user-service — we
 * fall back to a userId prefix if the lookup blips so the byline is
 * never blank.
 */
@Builder
public record AnnouncementResponse(
        String id,
        String buildingId,
        String authorUserId,
        String authorName,
        String title,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
