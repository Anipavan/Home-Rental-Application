package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;

import java.time.Instant;
import java.util.List;

public record MaintenanceRequestResponse(
        String id,
        String requestNumber,
        String tenantId,
        String flatId,
        String ownerId,
        Category category,
        String   title,
        String   description,
        Priority priority,
        Status   status,
        List<String> images,
        String   assignedTo,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        List<CommentResponse> comments,
        List<HistoryEntryResponse> history
) {
    public record CommentResponse(String userId, String comment, Instant timestamp) {}
    public record HistoryEntryResponse(Status fromStatus, Status toStatus, String changedBy, Instant timestamp) {}
}
