package com.spa.home_rental_application.payment_service.payment_service.service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.BankAccountSavedEvent;
import com.spa.home_rental_application.payment_service.payment_service.service.CashfreeVendorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Two Kafka triggers, one destination: any change to a user's bank
 * account OR their KYC state kicks off
 * {@link CashfreeVendorService#tryRegisterIfReady} for that user.
 *
 * <p>Both handlers are idempotent — the service checks the current
 * prereq state and either advances or no-ops. Failures are logged
 * and swallowed so the Kafka consumer commits and doesn't spin.
 */
@Component
@Slf4j
public class CashfreeVendorEventListener {

    private final CashfreeVendorService vendorService;

    public CashfreeVendorEventListener(CashfreeVendorService vendorService) {
        this.vendorService = vendorService;
    }

    /**
     * Listens on the user-events topic for the new
     * {@code user.bank-account.saved} event fired by user-service on
     * every bank-details save. Kicks off (or advances) vendor
     * registration for the owner.
     */
    @KafkaListener(
            topics = "${app.kafka.user-topic:user-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-payment-service}-cashfree-bank-saved",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.BankAccountSavedEvent"
            }
    )
    public void onBankAccountSaved(BankAccountSavedEvent event) {
        if (event == null) return;
        if (!"user.bank-account.saved".equals(event.getEventType())) return;
        log.info("Received {} userId={}", event.getEventType(), event.getUserId());
        try {
            vendorService.tryRegisterIfReady(event.getUserId());
        } catch (Exception ex) {
            log.error("tryRegisterIfReady failed for userId={} on bank-saved trigger",
                    event.getUserId(), ex);
        }
    }

    /**
     * Listens on the kyc-events topic for the existing
     * {@code kyc.verified} event fired by kyc-service. Kicks off (or
     * advances) vendor registration once the KYC leg completes.
     */
    @KafkaListener(
            topics = "${app.kafka.kyc-topic:kyc-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-payment-service}-cashfree-kyc-verified",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent"
            }
    )
    public void onKycVerified(KycVerifiedEvent event) {
        if (event == null) return;
        // KycVerifiedEvent doesn't set a stable eventType string on
        // every producer path; guard on the actual verified flag which
        // is a firmer signal for our use case.
        if (!Boolean.TRUE.equals(event.getVerified())) return;
        log.info("Received kyc.verified userId={}", event.getUserId());
        try {
            vendorService.tryRegisterIfReady(event.getUserId());
        } catch (Exception ex) {
            log.error("tryRegisterIfReady failed for userId={} on kyc-verified trigger",
                    event.getUserId(), ex);
        }
    }
}
