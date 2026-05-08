package com.spa.home_rental_application.user_service.user_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Subscribes to {@code kyc-events} (published by KYC Service) and flips the
 * user's {@code kyc_status} on the {@code users} table.
 * <p>
 * The update is idempotent — re-delivery of the same event lands on the
 * same {@code VERIFIED} or {@code FAILED} state, no extra side effects.
 */
@Component
@Slf4j
public class KycEventListener {

    private final UserRepo userRepo;

    public KycEventListener(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @KafkaListener(
            topics = "${app.kafka.kyc-topic:kyc-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-user-service}-kyc-verified",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent"}
    )
    @Transactional
    public void onKycVerified(KycVerifiedEvent e) {
        if (e == null || !"kyc.verified".equals(e.getEventType())) return;
        log.info("Received {} for userId={}", e.getEventType(), e.getUserId());
        int rows = userRepo.updateKycStatus(
                e.getUserId(),
                "VERIFIED",
                e.getKycProvider(),
                e.getVerifiedAt() != null ? e.getVerifiedAt() : LocalDateTime.now(),
                LocalDateTime.now());
        if (rows == 0) {
            // The user might not exist yet (event ordering on cold-start) —
            // log + drop. KYC Service can be re-driven manually if needed.
            log.warn("kyc.verified event for unknown userId={} (no row updated)", e.getUserId());
        }
    }

    @KafkaListener(
            topics = "${app.kafka.kyc-topic:kyc-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-user-service}-kyc-failed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent"}
    )
    @Transactional
    public void onKycFailed(KycFailedEvent e) {
        if (e == null || !"kyc.failed".equals(e.getEventType())) return;
        log.info("Received {} for userId={} reason={}",
                e.getEventType(), e.getUserId(), e.getFailureCode());
        int rows = userRepo.updateKycStatus(
                e.getUserId(),
                "FAILED",
                e.getKycProvider(),
                null,
                LocalDateTime.now());
        if (rows == 0) {
            log.warn("kyc.failed event for unknown userId={} (no row updated)", e.getUserId());
        }
    }
}
