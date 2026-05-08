package com.spa.home_rental_application.kyc_service.consumer;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.kyc_service.service.KycService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * On {@code user-events.user.profile.created}, idempotently create a PENDING
 * KYC record so the user can immediately see "KYC required" in the UI without
 * waiting for the first explicit /initiate call.
 */
@Component
@Slf4j
public class UserRegisteredConsumer {

    private final KycService kycService;

    public UserRegisteredConsumer(KycService kycService) {
        this.kycService = kycService;
    }

    @KafkaListener(
            topics = "${app.kafka.user-topic:user-events}",
            groupId = "hra-kyc-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onUserProfileCreated(UserProfileCreatedEvent event) {
        if (event == null || event.getUserId() == null) {
            log.warn("Ignoring user-events message with null userId");
            return;
        }
        if (!"user.profile.created".equalsIgnoreCase(event.getEventType())
                && event.getEventType() != null) {
            // Topic carries multiple event types — only react to the create one
            log.debug("Skipping user-event type={}", event.getEventType());
            return;
        }
        try {
            kycService.ensurePendingRecord(event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to seed PENDING KYC for userId={}", event.getUserId(), ex);
            // Swallow — re-throw would force redelivery and risk poison pill;
            // the user can still call /kyc/initiate directly to recover.
        }
    }
}
