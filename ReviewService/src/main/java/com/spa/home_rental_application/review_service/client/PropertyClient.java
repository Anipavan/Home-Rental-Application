package com.spa.home_rental_application.review_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client into property-service. Used to resolve a building's
 * {@code ownerId} (the property owner's auth user id) when a tenant
 * leaves a PROPERTY-targeted review — the notification fan-out needs
 * the owner's id, not the buildingId, to find their notification
 * preferences row.
 *
 * <p>Failures are absorbed by {@link PropertyClientFallback} so a
 * property-service outage doesn't tank the review-submission path —
 * the review still saves, the email just doesn't get sent (the
 * event ships with ownerAuthId=null and the notification listener
 * skips silently).
 */
@FeignClient(name = "HRA-property-service",
             fallback = PropertyClientFallback.class)
public interface PropertyClient {

    /** Mirrors property-service {@code GET /buildings/{buildingId}}. */
    @GetMapping("/buildings/{buildingId}")
    BuildingSummary getBuildingById(@PathVariable("buildingId") String buildingId);

    /**
     * Subset of property-service's BuildingResponseDTO that we actually
     * need. Jackson on the receiving side ignores unknown fields, so
     * extra fields on the wire are tolerated.
     */
    record BuildingSummary(
            String buildingId,
            String buildingName,
            String ownerId
    ) {}
}
