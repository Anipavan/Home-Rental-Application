package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.ComplaintCategory;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;

import java.time.Instant;
import java.util.List;

/**
 * Response payload for both maintenance tickets and complaints.
 * {@code kind} discriminates; exactly one of
 * {@code category} / {@code complaintCategory} will be non-null
 * depending on the value.
 */
public record MaintenanceRequestResponse(
        String id,
        String requestNumber,
        String tenantId,
        String flatId,
        String ownerId,
        Kind kind,
        Category category,
        ComplaintCategory complaintCategory,
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
