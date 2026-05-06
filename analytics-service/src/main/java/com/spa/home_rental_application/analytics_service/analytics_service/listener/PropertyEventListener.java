package com.spa.home_rental_application.analytics_service.analytics_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PropertyEventListener {

    private final AggregationService agg;

    public PropertyEventListener(AggregationService agg) {
        this.agg = agg;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-analytics-service}-flat-occupied",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent"}
    )
    public void onOccupied(FlatOccupiedEvent e) {
        if (e == null || !"flat.occupied".equals(e.getEventType())) return;
        log.info("Received {} buildingId={}", e.getEventType(), e.getBuildingId());
        agg.onFlatOccupied(e.getBuildingId());
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-analytics-service}-flat-vacated",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent"}
    )
    public void onVacated(FlatVacatedEvent e) {
        if (e == null || !"flat.vacated".equals(e.getEventType())) return;
        log.info("Received {} flatId={}", e.getEventType(), e.getFlatId());
        // FlatVacatedEvent doesn't carry buildingId — best-effort attribute to flatId
        // so each flat-level vacate at least decrements *something*. Real fix is to
        // add buildingId to the producer; flagged as v2.
        agg.onFlatVacated(e.getFlatId());
    }
}
