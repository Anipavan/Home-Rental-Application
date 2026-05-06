package com.spa.home_rental_application.analytics_service.analytics_service.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.RevenueResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generates Excel (Apache POI) and PDF (OpenPDF) reports from any list of
 * {@link RevenueResponse} rows. Returns the bytes; controllers stream them
 * straight to the HTTP response so we never write to disk in the hot path.
 */
@Service
@Slf4j
public class ReportExportService {

    public byte[] revenueToExcel(List<RevenueResponse> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Revenue");

            // Header
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            String[] headers = {"Owner ID", "Year", "Month", "Total Revenue",
                    "Total Paid", "Total Pending", "Total Overdue", "Payment Count"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Data
            int r = 1;
            for (RevenueResponse row : rows) {
                Row out_row = sheet.createRow(r++);
                out_row.createCell(0).setCellValue(safe(row.ownerId()));
                out_row.createCell(1).setCellValue(row.year());
                out_row.createCell(2).setCellValue(row.month());
                out_row.createCell(3).setCellValue(row.totalRevenue() == null ? 0 : row.totalRevenue().doubleValue());
                out_row.createCell(4).setCellValue(row.totalPaid() == null    ? 0 : row.totalPaid().doubleValue());
                out_row.createCell(5).setCellValue(row.totalPending() == null ? 0 : row.totalPending().doubleValue());
                out_row.createCell(6).setCellValue(row.totalOverdue() == null ? 0 : row.totalOverdue().doubleValue());
                out_row.createCell(7).setCellValue(row.paymentCount());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] revenueToPdf(List<RevenueResponse> rows) throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph("Revenue Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(12f);
            document.add(title);

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            String[] headers = {"Owner ID", "Year", "Month", "Total Revenue",
                    "Total Paid", "Total Pending", "Total Overdue", "Payments"};
            Font hf = new Font(Font.HELVETICA, 10, Font.BOLD);
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, hf));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                table.addCell(c);
            }
            for (RevenueResponse r : rows) {
                table.addCell(safe(r.ownerId()));
                table.addCell(String.valueOf(r.year()));
                table.addCell(String.valueOf(r.month()));
                table.addCell(r.totalRevenue() == null ? "0.00" : r.totalRevenue().toPlainString());
                table.addCell(r.totalPaid()    == null ? "0.00" : r.totalPaid().toPlainString());
                table.addCell(r.totalPending() == null ? "0.00" : r.totalPending().toPlainString());
                table.addCell(r.totalOverdue() == null ? "0.00" : r.totalOverdue().toPlainString());
                table.addCell(String.valueOf(r.paymentCount()));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
