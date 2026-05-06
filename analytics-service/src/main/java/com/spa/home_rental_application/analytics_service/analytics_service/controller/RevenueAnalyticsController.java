package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.ComparisonResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.RevenueResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/analytics/revenue", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics — Revenue", description = "Owner / monthly / yearly revenue + period comparison")
public class RevenueAnalyticsController {

    private final AnalyticsService service;

    public RevenueAnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Owner-level revenue summary (all months)")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<RevenueResponse>> owner(@PathVariable String ownerId) {
        return ResponseEntity.ok(service.ownerRevenue(ownerId));
    }

    @Operation(summary = "Monthly revenue across all owners for a given year")
    @GetMapping("/monthly/{year}")
    public ResponseEntity<List<RevenueResponse>> monthly(@PathVariable int year) {
        return ResponseEntity.ok(service.monthlyRevenue(year));
    }

    @Operation(summary = "Yearly total for a specific owner")
    @GetMapping("/yearly/{ownerId}/{year}")
    public ResponseEntity<RevenueResponse> yearlyForOwner(@PathVariable String ownerId,
                                                          @PathVariable int year) {
        return ResponseEntity.ok(service.yearlyTotalForOwner(ownerId, year));
    }

    @Operation(summary = "Month-over-month revenue comparison for an owner")
    @GetMapping("/comparison/{ownerId}")
    public ResponseEntity<ComparisonResponse> compare(@PathVariable String ownerId) {
        return ResponseEntity.ok(service.compareMonthOverMonth(ownerId));
    }
}
