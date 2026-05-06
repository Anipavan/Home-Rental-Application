package com.spa.home_rental_application.payment_service.payment_service.service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Subscribes to {@code property-events}. When a flat becomes occupied,
 * seed the first rent invoice for the new tenant.
 */
@Component
@Slf4j
public class FlatOccupiedListener {

    private final PaymentService paymentService;

    public FlatOccupiedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-payment-service}-flat-occupied",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent"
            }
    )
    public void onMessage(FlatOccupiedEvent event) {
        if (event == null || event.getEventType() == null) return;
        if (!"flat.occupied".equals(event.getEventType())) return;   // ignore other events on the same topic

        log.info("Received {} for flat={} tenant={}", event.getEventType(), event.getFlatId(), event.getTenantId());
        try {
            BigDecimal rent = event.getRentAmount() != null ? BigDecimal.valueOf(event.getRentAmount()) : BigDecimal.ZERO;
            LocalDate startDate = event.getStartDate() != null ? LocalDate.parse(event.getStartDate()) : LocalDate.now();
            paymentService.onFlatOccupied(event.getFlatId(), event.getTenantId(), rent, startDate);
        } catch (Exception ex) {
            log.error("Failed to seed payment for flat={}: {}", event.getFlatId(), ex.toString(), ex);
        }
    }
}
