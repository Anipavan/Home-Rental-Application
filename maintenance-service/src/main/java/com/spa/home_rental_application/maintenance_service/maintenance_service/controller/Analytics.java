package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.CategoryStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.ResolutionTimeStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/maintenance/stats", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Maintenance Analytics", description = "Aggregate metrics over maintenance requests")
public class Analytics {

    private final RequestService requestService;

    public Analytics(RequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "Count of pending requests (OPEN + IN_PROGRESS)")
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        return ResponseEntity.ok(Map.of("pendingCount", requestService.getPendingRequestCount()));
    }

    @Operation(summary = "Count of requests grouped by category")
    @GetMapping("/category")
    public ResponseEntity<List<CategoryStatsResponse>> getCategoryStats() {
        return ResponseEntity.ok(requestService.getCategoryStats());
    }

    @Operation(summary = "Average / min / max resolution time across resolved requests")
    @GetMapping("/resolution-time")
    public ResponseEntity<ResolutionTimeStatsResponse> getResolutionTimeStats() {
        return ResponseEntity.ok(requestService.getResolutionTimeStats());
    }
}
