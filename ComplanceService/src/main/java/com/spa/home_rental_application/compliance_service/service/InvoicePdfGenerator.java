package com.spa.home_rental_application.compliance_service.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.spa.home_rental_application.compliance_service.Entities.GstInvoice;
import com.spa.home_rental_application.compliance_service.Exceptionclass.PdfGenerationException;
import com.spa.home_rental_application.compliance_service.config.ComplianceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

/**
 * Generates a single-page A4 GST invoice PDF using OpenPDF (LGPL fork of
 * iText 4). Output is written to {@code app.compliance.invoice-storage-dir}
 * for now — swap to S3 in production.
 */
@Component
@Slf4j
public class InvoicePdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ComplianceProperties props;

    public InvoicePdfGenerator(ComplianceProperties props) {
        this.props = props;
    }

    /** Returns the local file path where the PDF was written. */
    public String generate(GstInvoice invoice) {
        Path dir = Paths.get(props.getInvoiceStorageDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new PdfGenerationException("Cannot create invoice dir: " + dir, e);
        }
        Path file = dir.resolve(invoice.getInvoiceNumber() + ".pdf");

        try (OutputStream os = Files.newOutputStream(file)) {
            Document document = new Document();
            PdfWriter.getInstance(document, os);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.BOLD);
            Paragraph title = new Paragraph("TAX INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("Invoice No: " + invoice.getInvoiceNumber(), header));
            document.add(new Paragraph(
                    "Date: " + (invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate().format(DATE)),
                    normal));
            document.add(new Paragraph("Owner ID: " + invoice.getOwnerId(), normal));
            document.add(new Paragraph("Tenant ID: " + invoice.getTenantId(), normal));
            document.add(new Paragraph("Payment Ref: " + invoice.getPaymentId(), normal));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(80);
            table.addCell(headerCell("Description"));
            table.addCell(headerCell("Amount (INR)"));

            table.addCell(cell("Rent for the period"));
            table.addCell(amountCell(invoice.getRentAmount()));

            if (Boolean.TRUE.equals(invoice.getGstApplicable())) {
                table.addCell(cell("GST @ " + invoice.getGstRatePercent() + "%"));
                table.addCell(amountCell(invoice.getGstAmount()));
            } else {
                table.addCell(cell("GST"));
                table.addCell(cell("Not applicable"));
            }

            table.addCell(headerCell("Total"));
            table.addCell(amountCell(invoice.getTotalAmount()));

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "This is a system-generated invoice. RentGenius — RERA-compliant property platform.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));

            document.close();
            log.info("Generated invoice PDF: {}", file);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new PdfGenerationException("Failed to write invoice PDF: " + file, e);
        }
    }

    private PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Paragraph(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        return c;
    }

    private PdfPCell cell(String text) {
        return new PdfPCell(new Paragraph(text,
                FontFactory.getFont(FontFactory.HELVETICA, 11)));
    }

    private PdfPCell amountCell(java.math.BigDecimal amount) {
        PdfPCell c = new PdfPCell(new Paragraph(
                amount == null ? "-" : amount.toPlainString(),
                FontFactory.getFont(FontFactory.HELVETICA, 11)));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }
}
