package com.spa.home_rental_application.lease_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client to fetch a RERA stamp from Compliance Service when generating
 * a lease deed. Failures fall back to {@link ComplianceClientFallback} so a
 * Compliance outage doesn't block lease creation — the deed is just stamped
 * "RERA: pending".
 */
@FeignClient(name = "HRA-compliance-service", fallback = ComplianceClientFallback.class)
public interface ComplianceClient {

    @PostMapping("/compliance/lease/generate-rera/{leaseId}")
    Map<String, String> generateReraMetadata(@PathVariable("leaseId") String leaseId,
                                             @RequestBody GenerateReraLeaseDto request);

    record GenerateReraLeaseDto(String leaseId, String propertyId, String state) {}
}
