package com.spa.home_rental_application.payment_service.payment_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fail-safe fallback for {@link PropertyClient}. Property-service being
 * down must NOT take down the payment auto-seeding path; we just lose
 * the {@code ownerId} on the new row and rely on the lazy back-fill in
 * {@code getPaymentsByOwner} to repair it on the next owner-facing
 * read.
 */
@Component
@Slf4j
public class PropertyClientFallback implements PropertyClient {

    @Override
    public PropertyClient.BuildingSummary getBuildingById(String buildingId) {
        log.warn("property-service unavailable — getBuildingById({}) falling back to empty", buildingId);
        return BuildingSummary.empty();
    }

    @Override
    public List<PropertyClient.BuildingSummary> getBuildingsByOwner(String ownerId) {
        log.warn("property-service unavailable — getBuildingsByOwner({}) falling back to empty", ownerId);
        return Collections.emptyList();
    }

    @Override
    public PropertyClient.FlatSummary getFlatById(String flatId) {
        log.warn("property-service unavailable — getFlatById({}) falling back to empty", flatId);
        return FlatSummary.empty();
    }

    @Override
    public List<PropertyClient.FlatSummary> getFlatsByBuilding(String buildingId) {
        log.warn("property-service unavailable — getFlatsByBuilding({}) falling back to empty", buildingId);
        return Collections.emptyList();
    }
}
