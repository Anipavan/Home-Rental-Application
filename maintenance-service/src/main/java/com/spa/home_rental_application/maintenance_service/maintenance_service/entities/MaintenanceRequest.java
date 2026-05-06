package com.spa.home_rental_application.maintenance_service.maintenance_service.entities;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance request stored in MongoDB. Uses enums for Category/Priority/
 * Status (validated upfront), tracks audit + status-change history, and
 * keeps comments inline.
 */
@Document(collection = "maintenance_requests")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceRequest {

    @Id
    private String id;

    @Field("request_number")
    @Indexed(unique = true)
    private String requestNumber;

    @Field("tenant_id")
    @Indexed
    private String tenantId;

    @Field("flat_id")
    @Indexed
    private String flatId;

    @Field("owner_id")
    @Indexed
    private String ownerId;

    private Category category;

    private String title;

    private String description;

    private Priority priority;

    @Indexed
    private Status status;

    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Field("assigned_to")
    private String assignedTo;

    @Field("created_at")
    @CreatedDate
    private Instant createdAt;

    @Field("updated_at")
    @LastModifiedDate
    private Instant updatedAt;

    @Field("resolved_at")
    private Instant resolvedAt;

    @Field("closed_at")
    private Instant closedAt;

    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @Builder.Default
    private List<HistoryEntry> history = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Comment {
        @Field("user_id") private String userId;
        private String comment;
        private Instant timestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class HistoryEntry {
        private Status fromStatus;
        private Status toStatus;
        @Field("changed_by") private String changedBy;
        private Instant timestamp;
    }
}
