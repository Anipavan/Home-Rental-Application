package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycPanVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code kyc-events} (published by KYC Service).
 * <ul>
 *   <li>{@code kyc.verified}     → success email + SMS</li>
 *   <li>{@code kyc.failed}       → failure email + SMS</li>
 *   <li>{@code kyc.pan.verified} → PAN-only success email</li>
 * </ul>
 */
@Component
@Slf4j
public class KycEventListener {

    private final NotificationService notifications;

    public KycEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.kyc-topic:kyc-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-kyc-verified",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent"}
    )
    public void onKycVerified(KycVerifiedEvent e) {
        if (e == null || !"kyc.verified".equals(e.getEventType())) return;
        log.info("Received {} for userId={}", e.getEventType(), e.getUserId());
        Map<String, Object> vars = Map.of(
                "kycProvider",     safe(e.getKycProvider()),
                "kycReferenceId",  safe(e.getKycReferenceId()),
                "verifiedAt",      safe(e.getVerifiedAt()));
        notifications.sendFromTemplate(e.getUserId(), NotificationType.EMAIL,
                NotificationCategory.KYC_VERIFIED, vars);
        notifications.sendFromTemplate(e.getUserId(), NotificationType.SMS,
                NotificationCategory.KYC_VERIFIED, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.kyc-topic:kyc-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-kyc-failed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent"}
    )
    public void onKycFailed(KycFailedEvent e) {
        if (e == null || !"kyc.failed".equals(e.getEventType())) return;
        log.info("Received {} for userId={} reason={}", e.getEventType(), e.getUserId(), e.getFailureCode());
        Map<String, Object> vars = Map.of(
                "kycProvider",   safe(e.getKycProvider()),
                "failureCode",   safe(e.getFailureCode()),
                "failureReason", safe(e.getFailureReason()));
        notifications.sendFromTemplate(e.getUserId(), NotificationType.EMAIL,
                NotificationCategory.KYC_FAILED, vars);
        notifications.sendFromTemplate(e.getUserId(), NotificationType.SMS,
                NotificationCategory.KYC_FAILED, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.kyc-topic:kyc-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-kyc-pan-verified",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycPanVerifiedEvent"}
    )
    public void onKycPanVerified(KycPanVerifiedEvent e) {
        if (e == null || !"kyc.pan.verified".equals(e.getEventType())) return;
        log.info("Received {} for userId={}", e.getEventType(), e.getUserId());
        notifications.sendFromTemplate(e.getUserId(), NotificationType.EMAIL,
                NotificationCategory.KYC_PAN_VERIFIED,
                Map.of("panHolderName", safe(e.getPanHolderName()),
                        "verifiedAt",   safe(e.getVerifiedAt())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
