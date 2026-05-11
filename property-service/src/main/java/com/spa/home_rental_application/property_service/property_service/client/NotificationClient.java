package com.spa.home_rental_application.property_service.property_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client for inter-service notification dispatch. Used by the
 * saved-search matcher to fire an "your alert matched a new flat"
 * email — going via Feign + Eureka avoids the gateway hop and the
 * internal-auth HMAC signing, matching the existing {@link UserClient}
 * pattern.
 *
 * <p>The {@code SendBody} mirrors notification-service's
 * {@code SendNotificationRequest} record but is intentionally a local,
 * loosely-typed DTO (type as String) so property-service doesn't take
 * a hard compile-time dependency on notification-service classes.
 */
@FeignClient(name = "HRA-notification-service", fallback = NotificationClientFallback.class)
public interface NotificationClient {

    /** Notification-service exposes {@code POST /notifications/send/email}. */
    @PostMapping(value = "/notifications/send/email", consumes = MediaType.APPLICATION_JSON_VALUE)
    void sendEmail(@RequestBody SendBody body);

    /**
     * Inbound payload for /notifications/send/email. Field names + JSON
     * shape match notification-service's SendNotificationRequest exactly
     * so Jackson binds 1:1 on the other side.
     */
    record SendBody(
            String userId,
            String type,                       // EMAIL | SMS | PUSH | WHATSAPP | INAPP
            String category,                   // optional template category
            String subject,
            String message,
            String recipient,                  // optional address override
            Map<String, Object> templateVariables
    ) {
        public static SendBody plainEmail(String userId, String subject, String message) {
            return new SendBody(userId, "EMAIL", null, subject, message, null, null);
        }
    }
}
