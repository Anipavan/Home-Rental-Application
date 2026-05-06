package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.MaintenanceMetricResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/analytics/maintenance", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics — Maintenance", description = "Resolution time, by-category breakdown")
public class MaintenanceAnalyticsController {

    private final AnalyticsService service;

    public MaintenanceAnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Resolved-count + average resolution time per category")
    @GetMapping("/by-category")
    public ResponseEntity<List<MaintenanceMetricResponse>> byCategory() {
        return ResponseEntity.ok(service.maintenanceByCategory());
    }

    @Operation(summary = "Weighted-average resolution time across every category")
    @GetMapping("/resolution-time")
    public ResponseEntity<Map<String, Double>> resolutionTime() {
        return ResponseEntity.ok(Map.of("avgResolutionMinutes", service.averageResolutionMinutes()));
    }
}
