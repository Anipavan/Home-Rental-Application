package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient;
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
import java.util.List;

/**
 * Generates one-page A4 PDF receipts and invoices, streamed back as raw
 * bytes (no on-disk persistence — payments are cheap to re-render on
 * demand and we don't have S3 here yet).
 *
 * <p>Branches by {@link Payment#getSourceType()}:
 * <ul>
 *   <li>{@code RENT} (default) — "RENT PAYMENT RECEIPT" heading, single
 *       "Rent" line item. Original behaviour, unchanged.</li>
 *   <li>{@code SOCIETY_CHARGE} — "MAINTENANCE RECEIPT" heading; if the
 *       Payment was a bulk Pay-all, each linked maintenance_collection
 *       row prints as its own line (Water bill, Maintenance, etc.) so
 *       the user sees what they actually paid for. Empty list (Feign
 *       fallback) collapses to a single "Society charges" summary line.</li>
 * </ul>
 *
 * <p>Tenant name and flat number are resolved via Feign at render time
 * so the receipt prints human-readable identifiers instead of UUIDs.
 * Both fall back to the raw id when their service is unreachable —
 * receipt download never 500s.
 *
 * <p>Uses OpenPDF (LGPL fork of iText 4) so we don't need a commercial
 * iText license.
 */
@Component
@Slf4j
public class PaymentPdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    /**
     * Renders the receipt for a captured payment as a PDF byte stream.
     *
     * @param p             the captured payment
     * @param r             the receipt row tied to this payment
     * @param tenantName    resolved "First Last" string — may be null when
     *                      user-service is unreachable, in which case the
     *                      PDF prints the raw authUserId instead.
     * @param flatNumber    resolved flat number (e.g. "101") — may be null
     *                      when property-service is unreachable, in which
     *                      case the PDF prints the raw flat UUID.
     * @param societyLines  every {@code maintenance_collection} row tied
     *                      to this payment. Empty for rent payments AND
     *                      for any society payment whose linkage couldn't
     *                      be resolved — both fall through to the legacy
     *                      single-line table. Non-empty triggers the
     *                      itemised maintenance-receipt layout.
     */
    public byte[] generateReceipt(Payment p,
                                  Receipt r,
                                  String tenantName,
                                  String flatNumber,
                                  List<PropertyClient.SocietyChargeLine> societyLines) {
        boolean isSocietyCharge = "SOCIETY_CHARGE".equals(p.getSourceType());
        boolean hasItemisedLines = societyLines != null && !societyLines.isEmpty();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD);
            Paragraph head = new Paragraph(
                    isSocietyCharge ? "MAINTENANCE RECEIPT" : "RENT PAYMENT RECEIPT",
                    title);
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

            // Name (human-readable) instead of raw tenant id — fall back to
            // the id only when the Feign lookup couldn't resolve a profile.
            // Payment ID + Owner ID were shown here previously but they
            // read like internal noise to residents (long UUIDs / numeric
            // ids they don't recognise). Receipt No above + Transaction
            // Ref below are the identifiers users actually need for a
            // dispute; drop the internal ones from the printed surface.
            doc.add(kv("Name",
                    tenantName != null && !tenantName.isBlank()
                            ? tenantName
                            : safe(p.getTenantId()),
                    bold, normal));
            // Flat No (e.g. "101") instead of raw flat UUID — same fallback rule.
            doc.add(kv("Flat No",
                    flatNumber != null && !flatNumber.isBlank()
                            ? flatNumber
                            : safe(p.getFlatId()),
                    bold, normal));
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

            PdfPTable t;
            if (isSocietyCharge && hasItemisedLines) {
                // Itemised society receipt — one row per charge category so
                // the resident sees exactly what they paid for. Three
                // columns: Description, For Month, Amount.
                t = new PdfPTable(3);
                t.setWidthPercentage(100);
                t.setWidths(new float[]{45f, 25f, 30f});
                t.addCell(headerCell("Description"));
                t.addCell(headerCell("For Month"));
                t.addCell(headerCell("Amount (INR)"));

                for (PropertyClient.SocietyChargeLine line : societyLines) {
                    t.addCell(cell(prettyCategory(line.category())));
                    t.addCell(cell(line.forMonth() == null ? "-" : line.forMonth()));
                    t.addCell(amountCell(line.amountDue()));
                }
                if (p.getLateFee() != null && p.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
                    t.addCell(cell("Late fee"));
                    t.addCell(cell("-"));
                    t.addCell(amountCell(p.getLateFee()));
                }
                t.addCell(headerCell("Total Paid"));
                t.addCell(headerCell(""));
                t.addCell(amountCell(p.getTotalAmount()));
            } else {
                // Single-line layout: rent invoices, or society payments
                // whose collection-linkage couldn't be resolved.
                t = new PdfPTable(2);
                t.setWidthPercentage(100);
                t.addCell(headerCell("Description"));
                t.addCell(headerCell("Amount (INR)"));

                t.addCell(cell(isSocietyCharge ? "Society charges" : "Rent"));
                t.addCell(amountCell(p.getAmount()));
                if (p.getLateFee() != null && p.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
                    t.addCell(cell("Late fee"));
                    t.addCell(amountCell(p.getLateFee()));
                }
                t.addCell(headerCell("Total Paid"));
                t.addCell(amountCell(p.getTotalAmount()));
            }
            doc.add(t);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "This is a system-generated receipt. Anirudh Homes — verified rental platform.",
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

            boolean isSocietyCharge = "SOCIETY_CHARGE".equals(p.getSourceType());
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD);
            Paragraph head = new Paragraph(
                    isSocietyCharge ? "MAINTENANCE INVOICE" : "RENT INVOICE",
                    title);
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

            t.addCell(cell(isSocietyCharge ? "Society charges for the period" : "Rent for the period"));
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
                    "Please pay before the due date to avoid late fees. — Anirudh Homes",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice PDF for payment {}", p.getId(), e);
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    /* ------------------------------ helpers ------------------------------ */

    /**
     * Convert raw MaintenanceCategory enum names into something a resident
     * would actually expect to see on a receipt. "WATER_BILL" → "Water
     * bill", "COMMON_AREA_SHARE" → "Common-area share", etc. Unknown
     * values fall through capitalised so a new category added server-side
     * still renders sensibly without a code change here.
     */
    private static String prettyCategory(String raw) {
        if (raw == null || raw.isBlank()) return "Other";
        switch (raw) {
            case "WATER_BILL":        return "Water bill";
            case "MAINTENANCE":       return "Maintenance";
            case "GAS_BILL":          return "Gas bill";
            case "ELECTRICITY":       return "Electricity";
            case "COMMON_AREA_SHARE": return "Common-area share";
            case "OTHER":             return "Other";
            default:
                // Fallback: capitalise first letter, lowercase the rest,
                // replace underscores with spaces — "FUTURE_CATEGORY"
                // becomes "Future category".
                String lower = raw.toLowerCase().replace('_', ' ');
                return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }

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
