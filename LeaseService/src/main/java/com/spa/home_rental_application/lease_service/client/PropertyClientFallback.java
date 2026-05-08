package com.spa.home_rental_application.lease_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PropertyClientFallback implements PropertyClient {

    @Override
    public BuildingSummary getBuildingById(String buildingId) {
        log.warn("property-service unavailable — empty BuildingSummary for buildingId={}",
                buildingId);
        return BuildingSummary.empty();
    }

    @Override
    public FlatSummary getFlatById(String flatId) {
        log.warn("property-service unavailable — empty FlatSummary for flatId={}", flatId);
        return FlatSummary.empty();
    }
}
