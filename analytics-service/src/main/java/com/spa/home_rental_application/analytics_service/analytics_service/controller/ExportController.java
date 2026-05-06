package com.spa.home_rental_application.analytics_service.analytics_service.controller;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.RevenueResponse;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AnalyticsService;
import com.spa.home_rental_application.analytics_service.analytics_service.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Owner revenue as PDF")
    @GetMapping(value = "/revenue/pdf/{ownerId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> revenuePdf(@PathVariable String ownerId) throws Exception {
        List<RevenueResponse> rows = analytics.ownerRevenue(ownerId);
        byte[] pdf = exporter.revenueToPdf(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"revenue-" + ownerId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Owner revenue as Excel (.xlsx)")
    @GetMapping(value = "/revenue/excel/{ownerId}",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> revenueExcel(@PathVariable String ownerId) throws Exception {
        List<RevenueResponse> rows = analytics.ownerRevenue(ownerId);
        byte[] xlsx = exporter.revenueToExcel(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"revenue-" + ownerId + ".xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(xlsx);
    }
}
