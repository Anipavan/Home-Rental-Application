package com.spa.home_rental_application.analytics_service.analytics_service.service;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.RevenueResponse;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {

    private final ReportExportService svc = new ReportExportService();

    @Test
    void revenueToExcel_writesHeaderAndRows() throws Exception {
        var rows = List.of(
                new RevenueResponse("O1", 2026, 5, new BigDecimal("8500"),
                        new BigDecimal("8500"), BigDecimal.ZERO, BigDecimal.ZERO, 1L, Instant.now()),
                new RevenueResponse("O1", 2026, 6, new BigDecimal("8500"),
                        new BigDecimal("8500"), BigDecimal.ZERO, BigDecimal.ZERO, 1L, Instant.now()));

        byte[] bytes = svc.revenueToExcel(rows);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("Revenue");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Owner ID");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("O1");
            assertThat(sheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(6);
        }
    }

    @Test
    void revenueToPdf_returnsNonEmptyBytesStartingWithPdfHeader() throws Exception {
        var rows = List.of(new RevenueResponse("O1", 2026, 5, new BigDecimal("8500"),
                new BigDecimal("8500"), BigDecimal.ZERO, BigDecimal.ZERO, 1L, Instant.now()));
        byte[] pdf = svc.revenueToPdf(rows);
        assertThat(pdf).isNotEmpty();
        // PDFs start with "%PDF-"
        String header = new String(pdf, 0, 5);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    void revenueToExcel_emptyList_writesOnlyHeader() throws Exception {
        byte[] bytes = svc.revenueToExcel(List.of());
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("Revenue");
            assertThat(sheet.getLastRowNum()).isZero(); // only header row
        }
    }
}
