package com.spa.home_rental_application.notification_service.notification_service.entities;

import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Renderable template. Lookup is by (category, type) — one template per
 * category per channel. Body uses {@code {{var}}} placeholders that the
 * service substitutes from the event payload.
 */
@Document(collection = "notification_templates")
@CompoundIndex(name = "idx_templates_cat_type", def = "{ 'category': 1, 'type': 1 }", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    private String id;

    private String name;

    private NotificationCategory category;

    private NotificationType type;

    private String subject;

    @Field("body_template")
    private String bodyTemplate;

    /** Names of placeholders the body expects (informational only). */
    private List<String> variables;
}
