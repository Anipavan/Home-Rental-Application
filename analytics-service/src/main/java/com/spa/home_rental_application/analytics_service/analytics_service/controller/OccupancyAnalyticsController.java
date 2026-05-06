package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.OccupancyResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/analytics/occupancy", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics — Occupancy", description = "Per-building and overall flat occupancy")
public class OccupancyAnalyticsController {

    private final AnalyticsService service;

    public OccupancyAnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Daily occupancy snapshots for a building")
    @GetMapping("/building/{buildingId}")
    public ResponseEntity<List<OccupancyResponse>> building(@PathVariable String buildingId) {
        return ResponseEntity.ok(service.buildingOccupancyTrend(buildingId));
    }

    @Operation(summary = "Aggregate occupancy across every building today")
    @GetMapping("/overall")
    public ResponseEntity<OccupancyResponse> overall() {
        return ResponseEntity.ok(service.overallOccupancyForToday());
    }

    @Operation(summary = "Time series of occupancy for a single building (alias)")
    @GetMapping("/trend/{buildingId}")
    public ResponseEntity<List<OccupancyResponse>> trend(@PathVariable String buildingId) {
        return ResponseEntity.ok(service.buildingOccupancyTrend(buildingId));
    }
}
