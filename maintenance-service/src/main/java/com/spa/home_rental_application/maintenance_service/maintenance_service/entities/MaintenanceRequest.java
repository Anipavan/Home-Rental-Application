package com.spa.home_rental_application.maintenance_service.maintenance_service.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;

@Document(collection = "maintenance_requests")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class MaintenanceRequest {

    @Id
    private String id;

    @Field("request_number")
    private String requestNumber;

    @Field("tenant_id")
    private String tenantId;

    @Field("flat_id")
    private String flatId;

    private String category;   // PLUMBING, ELECTRICAL, etc.
    private String title;
    private String description;
    private String priority;   // LOW, MEDIUM, HIGH, CRITICAL
    private String status;     // OPEN, IN_PROGRESS, RESOLVED, CLOSED

    private List<String> images; // URLs

    @Field("assigned_to")
    private String assignedTo;   // Technician/Owner ID

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("resolved_at")
    private Date resolvedAt;

    private List<Comment> comments;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Comment {

        @Field("user_id")
        private Long userId;

        private String comment;

        private Date timestamp;
    }
}
