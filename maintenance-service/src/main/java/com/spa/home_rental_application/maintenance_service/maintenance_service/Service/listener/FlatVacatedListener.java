package com.spa.home_rental_application.maintenance_service.maintenance_service.Service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the {@code property-events} topic and reacts to
 * {@code flat.vacated}: every active maintenance request for that flat is
 * auto-closed with actor {@code SYSTEM (flat.vacated)}.
 */
@Component
@Slf4j
public class FlatVacatedListener {

    private final RequestService requestService;

    public FlatVacatedListener(RequestService requestService) {
        this.requestService = requestService;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-maintenance-service}"
    )
    public void onMessage(FlatVacatedEvent event) {
        if (event == null || event.getEventType() == null) return;
        if (!"flat.vacated".equals(event.getEventType())) {
            // Ignore other property-events (property.created, etc.)
            return;
        }
        log.info("Received {} for flatId={} tenantId={}",
                event.getEventType(), event.getFlatId(), event.getTenantId());
        try {
            requestService.onFlatVacated(event.getFlatId(), event.getTenantId());
        } catch (Exception ex) {
            log.error("Failed to auto-close requests for flat {}: {}", event.getFlatId(), ex.toString(), ex);
            // swallow — the consumer must not block other event processing
        }
    }
}
