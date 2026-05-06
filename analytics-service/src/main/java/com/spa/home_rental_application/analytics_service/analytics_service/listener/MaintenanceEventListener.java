package com.spa.home_rental_application.analytics_service.analytics_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceResolvedEvent;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Two listeners:
 *  - {@code maintenance.created} — caches the request's category so we
 *    can attribute the resolution metric to it later (resolved event
 *    only carries requestId, not category).
 *  - {@code maintenance.resolved} — increments the per-category metric.
 */
@Component
@Slf4j
public class MaintenanceEventListener {

    private final AggregationService agg;

    /**
     * In-memory category cache for active requests. Single-instance only;
     * a multi-instance deployment should swap this for Redis or have the
     * resolver event include the category directly. Flagged as v2 in the
     * platform README.
     */
    private final ConcurrentMap<String, String> requestCategory = new ConcurrentHashMap<>();

    public MaintenanceEventListener(AggregationService agg) {
        this.agg = agg;
    }

    @KafkaListener(
            topics = "${app.kafka.maintenance-topic:maintenance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-analytics-service}-maintenance-created",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceCreatedEvent"}
    )
    public void onCreated(MaintenanceCreatedEvent e) {
        if (e == null || !"maintenance.created".equals(e.getEventType())) return;
        if (e.getRequestId() != null && e.getCategory() != null) {
            requestCategory.put(e.getRequestId(), e.getCategory());
        }
    }

    @KafkaListener(
            topics = "${app.kafka.maintenance-topic:maintenance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-analytics-service}-maintenance-resolved",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceResolvedEvent"}
    )
    public void onResolved(MaintenanceResolvedEvent e) {
        if (e == null || !"maintenance.resolved".equals(e.getEventType())) return;
        log.info("Received {} requestId={} resolutionMin={}",
                e.getEventType(), e.getRequestId(), e.getResolutionTimeMinutes());
        String category = requestCategory.remove(e.getRequestId());
        if (category == null) category = "UNKNOWN";
        agg.onMaintenanceResolved(category, e.getResolutionTimeMinutes());
    }
}
