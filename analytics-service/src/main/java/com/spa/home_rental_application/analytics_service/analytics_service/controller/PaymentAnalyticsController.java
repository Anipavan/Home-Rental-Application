package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.PaymentTrendResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/analytics/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics — Payments", description = "Collection efficiency, late-payment trends")
public class PaymentAnalyticsController {

    private final AnalyticsService service;

    public PaymentAnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Overall on-time-vs-late collection rate for an owner")
    @GetMapping("/collection-rate/{ownerId}")
    public ResponseEntity<Map<String, Double>> collectionRate(@PathVariable String ownerId) {
        return ResponseEntity.ok(Map.of("collectionRate", service.overallCollectionRateForOwner(ownerId)));
    }

    @Operation(summary = "Per-month payment trends for an owner")
    @GetMapping("/trends/{ownerId}")
    public ResponseEntity<List<PaymentTrendResponse>> trends(@PathVariable String ownerId) {
        return ResponseEntity.ok(service.trendsForOwner(ownerId));
    }
}
