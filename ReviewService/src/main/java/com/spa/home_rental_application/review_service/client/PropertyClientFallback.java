package com.spa.home_rental_application.review_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback that returns null on every method when property-service is
 * unreachable. ReviewServiceImpl's call site treats null as "couldn't
 * resolve owner — skip the owner-side notification" rather than
 * failing the review submission.
 */
@Component
@Slf4j
public class PropertyClientFallback implements PropertyClient {

    @Override
    public BuildingSummary getBuildingById(String buildingId) {
        log.warn("property-service unavailable — falling back to null for buildingId={}",
                buildingId);
        return null;
    }
}
