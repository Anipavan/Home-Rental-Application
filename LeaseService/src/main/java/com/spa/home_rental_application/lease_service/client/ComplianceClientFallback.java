package com.spa.home_rental_application.lease_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ComplianceClientFallback implements ComplianceClient {

    @Override
    public Map<String, String> generateReraMetadata(String leaseId, GenerateReraLeaseDto request) {
        log.warn("Compliance Service unavailable — using fallback RERA stamp for leaseId={}", leaseId);
        return Map.of("leaseId", leaseId,
                "reraMetadata", "RERA: pending (compliance service unavailable)");
    }
}
