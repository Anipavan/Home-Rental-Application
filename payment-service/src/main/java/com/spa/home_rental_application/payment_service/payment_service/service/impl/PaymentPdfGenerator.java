package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.spa.home_rental_application.payment_service.payment_service.entities.Invoice;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.entities.Receipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates one-page A4 PDF receipts and invoices for rent payments,
 * streamed back as raw bytes (no on-disk persistence — payments are
 * cheap to re-render on demand and we don't have S3 here yet).
 *
 * <p>Uses OpenPDF (LGPL fork of iText 4) so we don't need a commercial
 * iText license. The Compliance service uses the same library for GST
 * invoices, see {@code InvoicePdfGenerator}.
 */
@Component
@Slf4j
public class PaymentPdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    /** Renders the receipt for a captured payment as a PDF byte stream. */
    public byte[] generateReceipt(Payment p, Receipt r) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD);
            Paragraph head = new Paragraph("RENT PAYMENT RECEIPT", title);
            head.setAlignment(Element.ALIGN_CENTER);
            doc.add(head);
            doc.add(new Paragraph(" "));

            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            doc.add(kv("Receipt No", r.getReceiptNumber(), bold, normal));
            doc.add(kv("Issued",
                    r.getGeneratedDate() == null
                            ? formatInstant(Instant.now())
                            : formatInstant(r.getGeneratedDate()),
                    bold, normal));
            doc.add(new Paragraph(" "));

            doc.add(kv("Payment ID", p.getId(), bold, normal));
            doc.add(kv("Tenant ID", safe(p.getTenantId()), bold, normal));
            doc.add(kv("Flat ID", safe(p.getFlatId()), bold, normal));
            doc.add(kv("Owner ID", safe(p.getOwnerId()), bold, normal));
            doc.add(kv("Due Date", p.getDueDate() == null ? "-" : DATE.format(p.getDueDate()), bold, normal));
            doc.add(kv("Paid On",
                    p.getPaymentDate() == null ? "-" : formatInstant(p.getPaymentDate()),
                    bold, normal));
            doc.add(kv("Payment Method",
                    p.getPaymentMethod() == null ? "-" : p.getPaymentMethod().name(),
                    bold, normal));
            if (p.getTransactionId() != null) {
                doc.add(kv("Transaction Ref", p.getTransactionId(), bold, normal));
            }
            doc.add(new Paragraph(" "));

            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            t.addCell(headerCell("Description"));
            t.addCell(headerCell("Amount (INR)"));

            t.addCell(cell("Rent"));
            t.addCell(amountCell(p.getAmount()));
            if (p.getLateFee() != null && p.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
                t.addCell(cell("Late fee"));
                t.addCell(amountCell(p.getLateFee()));
            }
            t.addCell(headerCell("Total Paid"));
            t.addCell(amountCell(p.getTotalAmount()));
            doc.add(t);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "This is a system-generated receipt. Hearth — verified rental platform.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate receipt PDF for payment {}", p.getId(), e);
            throw new IllegalStateException("Failed to generate receipt PDF", e);
        }
    }

    /** Renders the invoice (issued at payment-create time) as a PDF byte stream. */
    public byte[] generateInvoice(Payment p, Invoice inv) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD);
            Paragraph head = new Paragraph("RENT INVOICE", title);
            head.setAlignment(Element.ALIGN_CENTER);
            doc.add(head);
            doc.add(new Paragraph(" "));

            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            doc.add(kv("Invoice No", inv.getInvoiceNumber(), bold, normal));
            doc.add(kv("Issued",
                    inv.getGeneratedDate() == null
                            ? formatInstant(Instant.now())
                            : formatInstant(inv.getGeneratedDate()),
                    bold, normal));
            doc.add(kv("Due Date", p.getDueDate() == null ? "-" : DATE.format(p.getDueDate()),
                    bold, normal));
            doc.add(new Paragraph(" "));

            doc.add(kv("Payment ID", p.getId(), bold, normal));
            doc.add(kv("Tenant ID", safe(p.getTenantId()), bold, normal));
            doc.add(kv("Flat ID", safe(p.getFlatId()), bold, normal));
            doc.add(kv("Owner ID", safe(p.getOwnerId()), bold, normal));
            doc.add(new Paragraph(" "));

            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            t.addCell(headerCell("Description"));
            t.addCell(headerCell("Amount (INR)"));

            t.addCell(cell("Rent for the period"));
            t.addCell(amountCell(p.getAmount()));
            if (p.getLateFee() != null && p.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
                t.addCell(cell("Late fee"));
                t.addCell(amountCell(p.getLateFee()));
            }
            t.addCell(headerCell("Total Due"));
            t.addCell(amountCell(p.getTotalAmount()));
            doc.add(t);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Please pay before the due date to avoid late fees. — Hearth",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice PDF for payment {}", p.getId(), e);
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    /* ------------------------------ helpers ------------------------------ */

    private static String formatInstant(Instant i) {
        return DATETIME.format(i);
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }

    private static Paragraph kv(String key, String value, Font keyFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.add(new com.lowagie.text.Chunk(key + ": ", keyFont));
        p.add(new com.lowagie.text.Chunk(value == null ? "-" : value, valueFont));
        return p;
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

    private PdfPCell amountCell(BigDecimal amount) {
        PdfPCell c = new PdfPCell(new Paragraph(
                amount == null ? "-" : "Rs. " + amount.toPlainString(),
                FontFactory.getFont(FontFactory.HELVETICA, 11)));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }
}
