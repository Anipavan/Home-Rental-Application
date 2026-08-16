package com.spa.home_rental_application.payment_service.payment_service.service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.SocietyBankAccountSavedEvent;
import com.spa.home_rental_application.payment_service.payment_service.service.SocietyCashfreeVendorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Sibling of {@code CashfreeVendorEventListener} — consumes the
 * {@code society.bank-account.saved} event fired by property-service
 * whenever a maintainer saves / updates the society's bank + KYC on
 * the Society Page. Advances the per-society Cashfree vendor lifecycle
 * so tenant maintenance money can route to the society's own bank
 * account.
 *
 * <p>Idempotent — the service checks current state and either
 * registers, PATCHes, or no-ops.
 */
@Component
@Slf4j
public class SocietyCashfreeVendorEventListener {

    private final SocietyCashfreeVendorService vendorService;

    public SocietyCashfreeVendorEventListener(SocietyCashfreeVendorService vendorService) {
        this.vendorService = vendorService;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-payment-service}-society-cashfree-bank-saved",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.SocietyBankAccountSavedEvent"
            }
    )
    public void onSocietyBankAccountSaved(SocietyBankAccountSavedEvent event) {
        if (event == null) return;
        // Property-service publishes several event types onto the same
        // topic (property.created, flat.occupied, etc.). Only fire the
        // society-vendor lifecycle for the specific event we care about.
        if (!"society.bank-account.saved".equals(event.getEventType())) return;
        log.info("Received society.bank-account.saved buildingId={} last4={}",
                event.getBuildingId(), event.getAccountNumberLast4());
        try {
            vendorService.tryRegisterIfReadyForSociety(event.getBuildingId());
        } catch (Exception ex) {
            log.error("tryRegisterIfReadyForSociety failed for buildingId={}",
                    event.getBuildingId(), ex);
        }
    }
}
