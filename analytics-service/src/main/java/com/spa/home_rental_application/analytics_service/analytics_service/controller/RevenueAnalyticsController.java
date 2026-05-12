package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.ComparisonResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.RevenueResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.security.AnalyticsCallerSecurity;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

    /* Audit C9 (also H27/H28 from the audit): every per-owner endpoint
     * now requires the caller to BE that owner or be an admin. The
     * cross-tenant /monthly/{year} listing is admin-only because it
     * spans every owner. */

    @Operation(summary = "Owner-level revenue summary (self or ADMIN)")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<RevenueResponse>> owner(@PathVariable String ownerId,
                                                       HttpServletRequest req) {
        AnalyticsCallerSecurity.requireSelfOrAdmin(ownerId, req);
        return ResponseEntity.ok(service.ownerRevenue(ownerId));
    }

    @Operation(summary = "Monthly revenue across all owners (ADMIN only)")
    @GetMapping("/monthly/{year}")
    public ResponseEntity<List<RevenueResponse>> monthly(@PathVariable int year, HttpServletRequest req) {
        // Cross-tenant aggregation — admin-only. Owners use the
        // scoped /owner/{ownerId} or /yearly/{ownerId}/{year} routes.
        AnalyticsCallerSecurity.requireAdmin(req);
        return ResponseEntity.ok(service.monthlyRevenue(year));
    }

    @Operation(summary = "Yearly total for a specific owner (self or ADMIN)")
    @GetMapping("/yearly/{ownerId}/{year}")
    public ResponseEntity<RevenueResponse> yearlyForOwner(@PathVariable String ownerId,
                                                          @PathVariable int year,
                                                          HttpServletRequest req) {
        AnalyticsCallerSecurity.requireSelfOrAdmin(ownerId, req);
        return ResponseEntity.ok(service.yearlyTotalForOwner(ownerId, year));
    }

    @Operation(summary = "Month-over-month revenue comparison (self or ADMIN)")
    @GetMapping("/comparison/{ownerId}")
    public ResponseEntity<ComparisonResponse> compare(@PathVariable String ownerId,
                                                      HttpServletRequest req) {
        AnalyticsCallerSecurity.requireSelfOrAdmin(ownerId, req);
        return ResponseEntity.ok(service.compareMonthOverMonth(ownerId));
    }
}
