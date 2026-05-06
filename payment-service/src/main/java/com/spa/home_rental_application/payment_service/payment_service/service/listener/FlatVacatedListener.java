package com.spa.home_rental_application.payment_service.payment_service.service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code property-events}. When a flat is vacated, cancel
 * any active (PENDING/PROCESSING/OVERDUE) payments for it.
 */
@Component
@Slf4j
public class FlatVacatedListener {

    private final PaymentService paymentService;

    public FlatVacatedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-payment-service}-flat-vacated",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent"
            }
    )
    public void onMessage(FlatVacatedEvent event) {
        if (event == null || event.getEventType() == null) return;
        if (!"flat.vacated".equals(event.getEventType())) return;

        log.info("Received {} for flat={} tenant={}", event.getEventType(), event.getFlatId(), event.getTenantId());
        try {
            paymentService.onFlatVacated(event.getFlatId(), event.getTenantId());
        } catch (Exception ex) {
            log.error("Failed to cancel payments for flat={}: {}", event.getFlatId(), ex.toString(), ex);
        }
    }
}
