package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code auth-events}.
 * <ul>
 *   <li>{@code user.registered} → welcome / verify-email</li>
 *   <li>{@code user.password.reset.requested} → reset link email</li>
 * </ul>
 */
@Component
@Slf4j
public class AuthEventListener {

    private final NotificationService notifications;

    public AuthEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.auth-topic:auth-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-user-registered",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent"}
    )
    public void onUserRegistered(UserRegisteredEvent e) {
        if (e == null || !"user.registered".equals(e.getEventType())) return;
        log.info("Received {} for authUserId={}", e.getEventType(), e.getAuthUserId());
        notifications.sendFromTemplate(e.getAuthUserId(), NotificationType.EMAIL,
                NotificationCategory.USER_REGISTRATION,
                Map.of("userName", safe(e.getUserName()),
                        "email",   safe(e.getEmail()),
                        "role",    safe(e.getRole())));
    }

    @KafkaListener(
            topics = "${app.kafka.auth-topic:auth-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-password-reset",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent"}
    )
    public void onPasswordReset(PasswordResetRequestedEvent e) {
        if (e == null || !"user.password.reset.requested".equals(e.getEventType())) return;
        log.info("Received {} for authUserId={}", e.getEventType(), e.getAuthUserId());
        notifications.sendFromTemplate(e.getAuthUserId(), NotificationType.EMAIL,
                NotificationCategory.PASSWORD_RESET,
                Map.of("userName",  safe(e.getUserName()),
                        "email",    safe(e.getEmail()),
                        "token",    safe(e.getResetToken()),
                        "expiresAt",safe(e.getExpiresAt())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
