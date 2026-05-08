package com.spa.home_rental_application.lease_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client used by the lease-deed PDF generator to enrich the deed
 * with building/flat data pulled from property-service. Failures are
 * absorbed by {@link PropertyClientFallback} so deed generation falls back
 * to underline blanks for the address fields.
 */
@FeignClient(name = "HRA-property-service", fallback = PropertyClientFallback.class)
public interface PropertyClient {

    @GetMapping("/properties/buildings/{buildingId}")
    BuildingSummary getBuildingById(@PathVariable("buildingId") String buildingId);

    @GetMapping("/properties/flats/{flatId}")
    FlatSummary getFlatById(@PathVariable("flatId") String flatId);

    /** Subset of property-service BuildingResponseDTO used by the deed renderer. */
    record BuildingSummary(
            String buildingId,
            String buildingName,
            String buildingAddress,
            String buildingCity,
            String buildingState
    ) {
        public static BuildingSummary empty() {
            return new BuildingSummary(null, null, null, null, null);
        }
    }

    /** Subset of property-service FlatResponseDTO used by the deed renderer. */
    record FlatSummary(
            String id,
            String buildingId,
            String flatNumber,
            Integer floor,
            Integer bedrooms
    ) {
        public static FlatSummary empty() {
            return new FlatSummary(null, null, null, null, null);
        }
    }
}
