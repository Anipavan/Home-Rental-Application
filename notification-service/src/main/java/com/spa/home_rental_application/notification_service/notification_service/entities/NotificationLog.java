package com.spa.home_rental_application.notification_service.notification_service.entities;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * One row per notification attempt. Indexed on userId + status so the
 * retry job + UI lookups stay fast.
 */
@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Indexed
    private NotificationType type;

    @Indexed
    private NotificationCategory category;

    private String recipient;     // email address / phone / device token
    private String subject;
    private String message;

    @Indexed
    private NotificationStatus status;

    @Field("retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Field("error_message")
    private String errorMessage;

    @Field("sent_at")
    private Instant sentAt;

    @Field("delivered_at")
    private Instant deliveredAt;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;

    /** Ad-hoc bag of values pulled from the source event. Useful for debugging. */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
