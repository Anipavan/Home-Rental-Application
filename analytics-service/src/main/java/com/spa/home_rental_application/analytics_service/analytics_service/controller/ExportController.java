package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.RevenueResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import com.spa.home_rental_application.analytics_service.analytics_service.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics/export")
@Slf4j
@Tag(name = "Analytics — Export", description = "Download revenue reports as Excel or PDF")
public class ExportController {

    private final AnalyticsService analytics;
    private final ReportExportService exporter;

    public ExportController(AnalyticsService analytics, ReportExportService exporter) {
        this.analytics = analytics;
        this.exporter = exporter;
    }

    /* Audit C9: previously these endpoints accepted any ownerId path
     * parameter, letting one owner download a competitor's revenue
     * report. The new guard requires the caller to BE the requested
     * owner, or have the ADMIN role. */

    @Operation(summary = "Owner revenue as PDF (self or ADMIN)")
    @GetMapping(value = "/revenue/pdf/{ownerId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> revenuePdf(@PathVariable String ownerId,
                                             HttpServletRequest req) throws Exception {
        requireSelfOrAdmin(ownerId, req);
        List<RevenueResponse> rows = analytics.ownerRevenue(ownerId);
        byte[] pdf = exporter.revenueToPdf(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"revenue-" + ownerId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Owner revenue as Excel (self or ADMIN)")
    @GetMapping(value = "/revenue/excel/{ownerId}",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> revenueExcel(@PathVariable String ownerId,
                                               HttpServletRequest req) throws Exception {
        requireSelfOrAdmin(ownerId, req);
        List<RevenueResponse> rows = analytics.ownerRevenue(ownerId);
        byte[] xlsx = exporter.revenueToExcel(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"revenue-" + ownerId + ".xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(xlsx);
    }

    /** Delegate to the shared {@code AnalyticsCallerSecurity} for consistency. */
    private static void requireSelfOrAdmin(String ownerId, HttpServletRequest req) {
        com.spa.home_rental_application.analytics_service.analytics_service.security
                .AnalyticsCallerSecurity.requireSelfOrAdmin(ownerId, req);
    }
}
