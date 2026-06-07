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
     * Notification-service exposes {@code POST /notifications/internal/notify}.
     * Pushes BOTH a bell entry (INAPP) AND an email in one call —
     * the right surface for cross-service pings like "owner, someone
     * just applied to be your maintainer". The endpoint is gateway-
     * HMAC-only on the notification-service side, so this Feign call
     * (which adds the X-Internal-Auth-Sig header via the shared
     * signing interceptor) is the only way to reach it.
     */
    @PostMapping(value = "/notifications/internal/notify",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    void notifyUser(@RequestBody NotifyUserBody body);

    /** Matches notification-service's {@code InternalNotifyRequest}. */
    record NotifyUserBody(String userId, String subject, String message) {}

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
