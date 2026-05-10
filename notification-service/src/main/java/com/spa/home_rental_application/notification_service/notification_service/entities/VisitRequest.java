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
 * Property-visit request submitted from the public property-detail page.
 *
 * <p>Lives alongside {@link SupportTicket} in notification-service for the
 * same reason: small enough not to warrant its own service and the
 * notification-service already owns the autoresponder + email channel
 * we use to confirm receipt back to the visitor.
 *
 * <p>Distinct from a generic support ticket because:
 * <ul>
 *   <li>It has a {@code preferredAt} timestamp — admins / owners filter
 *       and sort by visit slot, not by ticket creation time.</li>
 *   <li>It pins the {@code flatId} / {@code buildingId} so the queue can
 *       be sliced by property without parsing free-text subject lines.</li>
 *   <li>Its status taxonomy is visit-specific:
 *       PENDING → CONFIRMED → COMPLETED, with CANCELLED as a terminal
 *       state instead of OPEN/RESOLVED.</li>
 * </ul>
 */
@Document(collection = "visit_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitRequest {

    @Id
    private String id;

    /**
     * authUserId of the submitter when signed in; the literal string
     * "PUBLIC_VISITOR" for anonymous public-page visitors. The frontend
     * always sends one or the other so this column is searchable.
     */
    @Indexed
    @Field("user_id")
    private String userId;

    @Field("visitor_name")
    private String visitorName;

    @Field("visitor_email")
    private String visitorEmail;

    @Field("visitor_phone")
    private String visitorPhone;

    /** Property the visitor wants to see. */
    @Indexed
    @Field("flat_id")
    private String flatId;

    @Field("building_id")
    private String buildingId;

    /**
     * Denormalised owner id at submit time so the owner-side
     * /owner/enquiries page can fetch every visit request about their
     * buildings without a cross-service join. Resolved from
     * {@code building.ownerId} on the public property-detail page when
     * the dialog is opened.
     */
    @Indexed
    @Field("owner_id")
    private String ownerId;

    /** Snapshot at submit time so the queue page doesn't have to re-resolve. */
    @Field("property_label")
    private String propertyLabel;

    /** When the visitor wants to come. Indexed for date-range filtering. */
    @Indexed
    @Field("preferred_at")
    private Instant preferredAt;

    private String message;

    /** Path the visitor was on when they submitted — useful debugging context. */
    @Field("context_url")
    private String contextUrl;

    @Indexed
    @Builder.Default
    private String status = "PENDING";   // PENDING | CONFIRMED | COMPLETED | CANCELLED

    /** Free-text response from the admin who handled the request. */
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
