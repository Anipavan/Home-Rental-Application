package com.spa.home_rental_application.user_service.user_service.service.External;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client into Property Service. Used by OwnerService to find which
 * users (tenants) currently occupy flats in any building owned by a given
 * owner — flat→tenant linkage is owned by Property Service.
 *
 * Wrapped by Resilience4j circuit breaker named {@code HRA-property-service}.
 * Falls back to an empty list via {@link PropertyServiceFeigFallbackFactory}
 * when Property Service is unreachable, so the owner profile endpoint still
 * returns the owner's basic info even if the tenant rollup is unavailable.
 */
@FeignClient(
        name = "HRA-property-service",
        fallbackFactory = PropertyServiceFeigFallbackFactory.class)
public interface PropertyServiceFeig {

    /**
     * Returns the {@code tenantId}s of every currently-occupied flat in any
     * building belonging to the given owner.
     */
    @GetMapping("/properties/buildings/owner/{ownerId}/tenants")
    List<String> getTenantIdsByOwner(@PathVariable("ownerId") String ownerId);
}
