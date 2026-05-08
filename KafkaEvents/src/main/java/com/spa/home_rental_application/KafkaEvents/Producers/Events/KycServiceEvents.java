package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycPanVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;

/**
 * Producer contract for KYC Service domain events.
 * Implemented in {@code com.spa.home_rental_application.KafkaEvents.Producers.Impliments.KycEventImpl}.
 */
public interface KycServiceEvents {
    void sendKycVerified(KycVerifiedEvent event);
    void sendKycFailed(KycFailedEvent event);
    void sendKycPanVerified(KycPanVerifiedEvent event);
}
