package com.spa.home_rental_application.notification_service.notification_service.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * In-app support ticket. Submitted from the frontend's "Contact support"
 * dropdown; admins see them under {@code /admin/support}.
 *
 * <p>Lives in the notification-service rather than its own service because
 * (a) it's small enough not to warrant a microservice, and (b) the
 * notification-service already owns the user-comms channel — it can spawn
 * an email reply when an admin responds.
 */
@Document(collection = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;            // authUserId of the submitter

    @Field("user_name")
    private String userName;

    @Field("user_email")
    private String userEmail;

    @Field("user_role")
    private String userRole;          // OWNER | TENANT | ADMIN

    private String subject;
    private String message;

    /**
     * Optional path the user was on when they raised the ticket — gives
     * the admin context without the user having to type "while paying rent".
     */
    private String contextUrl;

    @Indexed
    @Builder.Default
    private String status = "OPEN";   // OPEN | IN_PROGRESS | RESOLVED | CLOSED

    @Field("admin_response")
    private String adminResponse;

    @Field("responded_by")
    private String respondedBy;

    @Field("responded_at")
    private Instant respondedAt;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
