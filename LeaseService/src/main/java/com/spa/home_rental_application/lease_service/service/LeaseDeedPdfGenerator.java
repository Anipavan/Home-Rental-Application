package com.spa.home_rental_application.lease_service.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.spa.home_rental_application.lease_service.Entities.Lease;
import com.spa.home_rental_application.lease_service.Exceptionclass.LeaseDeedException;
import com.spa.home_rental_application.lease_service.client.PropertyClient.BuildingSummary;
import com.spa.home_rental_application.lease_service.client.PropertyClient.FlatSummary;
import com.spa.home_rental_application.lease_service.client.UserClient.UserSummary;
import com.spa.home_rental_application.lease_service.config.LeaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Renders a multi-page lease deed PDF that mirrors the standard Indian
 * residential rental-agreement template (21 clauses + Annexure-I) and
 * carries the RERA agreement number resolved from Compliance Service at
 * the top.
 *
 * <p>Lease-level data we have (lease number, RERA stamp, party IDs, term,
 * rent, security deposit, rent increment %) is filled in. Building / flat
 * address details aren't on the Lease entity and we don't currently have a
 * property-service Feign client wired into LeaseService — those fields are
 * rendered as underline blanks meant to be completed by hand on the printed
 * copy before notarization.
 *
 * <p>Output is written to {@code app.lease.deed-storage-dir}; swap to S3 in
 * production.
 */
@Component
@Slf4j
public class LeaseDeedPdfGenerator {

    private static final DateTimeFormatter PRETTY_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final DateTimeFormatter LONG_DAY =
            DateTimeFormatter.ofPattern("d");
    private static final DateTimeFormatter LONG_MONTH =
            DateTimeFormatter.ofPattern("MMMM");
    private static final DateTimeFormatter LONG_YEAR =
            DateTimeFormatter.ofPattern("yyyy");

    /** Standard underline used wherever the parties must hand-write. */
    private static final String BLANK = "_______________________";
    private static final String LONG_BLANK = "_________________________________________";

    private final LeaseProperties props;

    public LeaseDeedPdfGenerator(LeaseProperties props) {
        this.props = props;
    }

    /**
     * Backwards-compatible entry point — produces a deed without enrichment
     * data. All address / name fields render as underline blanks.
     */
    public String generate(Lease lease, String reraStamp) {
        return generate(lease, reraStamp, null, null, null, null);
    }

    /**
     * Renders the deed with optional building/flat/owner/tenant summaries.
     * Any null argument is treated as "fall back to handwritten blank" so
     * partial enrichment (e.g., user-service down but property-service up)
     * still produces a usable PDF.
     *
     * @return absolute path of the generated PDF.
     */
    public String generate(Lease lease,
                           String reraStamp,
                           BuildingSummary building,
                           FlatSummary flat,
                           UserSummary owner,
                           UserSummary tenant) {
        Path dir = Paths.get(props.getDeedStorageDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new LeaseDeedException("Cannot create lease deed dir: " + dir, e);
        }
        Path file = dir.resolve(lease.getLeaseNumber() + ".pdf");

        try (OutputStream os = Files.newOutputStream(file)) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, os);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font italic = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10);
            Font reraFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD);

            // ---------- TITLE & RERA STAMP ----------
            Paragraph title = new Paragraph("RENTAL AGREEMENT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(spacer());

            Paragraph leaseNo = new Paragraph(
                    "Lease No: " + nullSafe(lease.getLeaseNumber()), reraFont);
            leaseNo.setAlignment(Element.ALIGN_CENTER);
            doc.add(leaseNo);

            Paragraph rera = new Paragraph(
                    reraStamp == null || reraStamp.isBlank()
                            ? "RERA Agreement Number: pending"
                            : "RERA Agreement Number: " + reraStamp,
                    reraFont);
            rera.setAlignment(Element.ALIGN_CENTER);
            doc.add(rera);
            doc.add(spacer());

            // ---------- PREAMBLE ----------
            LocalDate executedOn = lease.getCreatedAt() != null
                    ? lease.getCreatedAt().toLocalDate()
                    : LocalDate.now();

            String place = building != null
                    ? joinNonBlank(", ",
                            building.buildingCity(),
                            building.buildingState())
                    : null;
            if (place == null || place.isBlank()) place = BLANK;
            doc.add(new Paragraph(
                    "This RENTAL AGREEMENT is executed at " + place
                            + " on this " + LONG_DAY.format(executedOn)
                            + " day of " + LONG_MONTH.format(executedOn)
                            + ", " + LONG_YEAR.format(executedOn)
                            + " by and between:",
                    body));
            doc.add(spacer());

            // ---------- OWNER BLOCK ----------
            String ownerName = owner != null && owner.fullName() != null
                    ? owner.fullName()
                    : BLANK;
            String ownerAddr = owner != null && owner.address() != null
                    && !owner.address().isBlank()
                    ? owner.address()
                    : LONG_BLANK;
            doc.add(new Paragraph("Name of the Owner: " + ownerName
                    + "  (Owner ID: " + nullSafe(lease.getOwnerId()) + ")", body));
            doc.add(new Paragraph("S/o or D/o: " + BLANK, body));
            doc.add(new Paragraph("Permanent Address: " + ownerAddr, body));
            doc.add(new Paragraph(
                    "(hereinafter jointly and severally called the \"OWNER\", which expression "
                            + "shall include their heirs, legal representatives, successors and "
                            + "assigns) of the ONE PART:",
                    body));
            doc.add(spacer());
            doc.add(new Paragraph("AND, in favour of:", body));
            doc.add(spacer());

            // ---------- TENANT BLOCK ----------
            String tenantName = tenant != null && tenant.fullName() != null
                    ? tenant.fullName()
                    : BLANK;
            String tenantAddr = tenant != null && tenant.address() != null
                    && !tenant.address().isBlank()
                    ? tenant.address()
                    : LONG_BLANK;
            doc.add(new Paragraph("Name of the Tenant: " + tenantName
                    + "  (Tenant ID: " + nullSafe(lease.getTenantId()) + ")", body));
            doc.add(new Paragraph("S/o or D/o: " + BLANK, body));
            doc.add(new Paragraph("Working/Studying at: " + LONG_BLANK, body));
            doc.add(new Paragraph("having a permanent address at: " + tenantAddr, body));
            doc.add(new Paragraph(
                    "(hereinafter called the \"TENANT\", which expression shall include its "
                            + "legal representatives, successors and assigns) of the OTHER PART.",
                    body));
            doc.add(spacer());

            // ---------- WHEREAS ----------
            String premisesAddr = formatPremisesAddress(building, flat);
            doc.add(new Paragraph(
                    "WHEREAS the Owner is the absolute owner of the property situated at "
                            + premisesAddr
                            + " (Flat ID: " + nullSafe(lease.getFlatId())
                            + ") as detailed in Annexure-I, hereinafter referred to as "
                            + "\"Demised Premises\".",
                    body));
            doc.add(spacer());
            doc.add(new Paragraph(
                    "WHEREAS the Tenant has requested the Owner to grant Rent with respect "
                            + "to the Schedule Premises and the Owner has agreed to rent out to "
                            + "the Tenant the Property with two-wheeler and four-wheeler parking "
                            + "space in the ground floor for residential purposes only, on the "
                            + "following terms and conditions:",
                    body));
            doc.add(spacer());

            doc.add(new Paragraph("NOW THIS DEED WITNESSETH AS FOLLOWS:", sectionFont));
            doc.add(spacer());

            // ---------- 21 CLAUSES ----------
            String startDate = lease.getStartDate() != null
                    ? PRETTY_DATE.format(lease.getStartDate())
                    : BLANK;
            String endDate = lease.getEndDate() != null
                    ? PRETTY_DATE.format(lease.getEndDate())
                    : BLANK;
            String rentAmount = lease.getRentAmount() != null
                    ? lease.getRentAmount().toPlainString()
                    : BLANK;
            String depositAmount = lease.getSecurityDeposit() != null
                    ? lease.getSecurityDeposit().toPlainString()
                    : BLANK;

            doc.add(clause(body,
                    "1. The rent in respect of the \"Demised Premises\" shall commence from "
                            + startDate + " and shall be valid till " + endDate
                            + ". Thereafter, the same may be extended further on mutual consent "
                            + "of both the parties."
                            + (lease.getRentIncrementPercent() != null
                                    && lease.getRentIncrementPercent().compareTo(BigDecimal.ZERO) > 0
                                ? " Annual rent escalation: "
                                    + lease.getRentIncrementPercent().toPlainString() + "%."
                                : "")));
            doc.add(clause(body,
                    "2. That the Tenant shall pay to the Owner a monthly rent of Rs. "
                            + rentAmount + ", excluding electricity and water bill. The rent "
                            + "shall be paid on or before 7th day of each month without fail."));
            doc.add(clause(body,
                    "3. That the Tenant shall pay to the Owner a monthly maintenance charge "
                            + "of Rs. " + BLANK + " towards the maintenance of Generator & "
                            + "Elevator, Salaries towards guards, Charges for Electricity "
                            + "Maintenance for Common Areas, Charges towards cleaning of Common "
                            + "Areas and towards maintaining the lawn."));
            doc.add(clause(body,
                    "4. That the Tenant shall pay for the running cost of elevator and "
                            + "generator separately to the Owner."));
            doc.add(clause(body,
                    "5. That during the Rent period, in addition to the rental amount payable "
                            + "to the Owner, the Tenant shall pay for the use of electricity "
                            + "and water as per bills received from the authorities concerned "
                            + "directly. For all the dues of electricity bills and water bills "
                            + "till the date the possession of the premises is handed over by "
                            + "the Owner to the Tenant it is the responsibility of the Owner to "
                            + "pay and clear them according to the readings on the respective "
                            + "meters. At the time of handing over possession of the premises "
                            + "back to the Owner by Tenant, it is the responsibility of the "
                            + "Tenant to pay electricity & water bills, as presented by the "
                            + "Departments concerned according to the readings on the respective "
                            + "meters upto the date of vacation of the property."));
            doc.add(clause(body,
                    "6. The Tenant will pay to the Owner an interest-free refundable security "
                            + "deposit of Rs. " + depositAmount + " vide cheque no " + BLANK
                            + " dated " + BLANK + " at the time of signing the Rent Agreement. "
                            + "The said amount of the Security deposit shall be refunded by the "
                            + "Owner to the Tenant at the time of handing over possession of the "
                            + "demised premises by the Tenant upon expiry or sooner termination "
                            + "of this Rent after adjusting any dues (if any) or cost towards "
                            + "damages caused by the negligence of the Tenant or the person he "
                            + "is responsible for, normal wear & tear and damages due to act of "
                            + "god exempted. In case the Owner fails to refund the security "
                            + "deposit to the Tenant on early termination or expiry of the Rent "
                            + "agreement, the Tenant is entitled to hold possession of the "
                            + "Rented premises, without payment of rent and/or any other charges "
                            + "whatsoever, till such time the Owner refunds the security deposit "
                            + "to the Tenant. This is without prejudice and in addition to the "
                            + "other remedies available to the Tenant to recover the amount from "
                            + "the Owner."));
            doc.add(clause(body,
                    "7. That all the sanitary, electrical and other fittings and fixtures and "
                            + "appliances in the premises shall be handed over from the Owner "
                            + "to the Tenant in good working condition."));
            doc.add(clause(body,
                    "8. That the Tenant shall not sublet, assign or part with the demised "
                            + "premises in whole or part thereof to any person in any "
                            + "circumstances whatsoever and the same shall be used for the "
                            + "bonafide residential purposes only."));
            doc.add(clause(body,
                    "9. That the day-to-day minor repairs will be the responsibility for the "
                            + "Tenant at his/her own expense. However, any structural or major "
                            + "repairs, if so required, shall be carried out by the Owner."));
            doc.add(clause(body,
                    "10. That no structural additions or alterations shall be made by the "
                            + "Tenant in the premises without the prior written consent of the "
                            + "Owner but the Tenant can install air-conditioners in the space "
                            + "provided and other electrical gadgets and make such changes for "
                            + "the purposes as may be necessary, at his own cost. On termination "
                            + "of the tenancy or earlier, the Tenant will be entitled to remove "
                            + "such equipment and restore the changes made, if any, to the "
                            + "original state."));
            doc.add(clause(body,
                    "11. That the Owner shall hold the right to visit in person or through "
                            + "his authorized agent(s), servants, workmen etc., to enter upon "
                            + "the demised premises for inspection (not exceeding once in a "
                            + "month) or to carry out repairs / construction, as and when "
                            + "required."));
            doc.add(clause(body,
                    "12. That the Tenant shall comply with all the rules and regulations of "
                            + "the local authority applicable to the demised premises. The "
                            + "premises will be used only for residential purposes of its "
                            + "employees, families and guests."));
            doc.add(clause(body,
                    "13. That the Owner shall pay for all taxes/cesses levied on the premises "
                            + "by the local or government authorities in the way of property "
                            + "tax for the premises and so on. Further, any other payment in "
                            + "the nature of subscription or periodical fee to the welfare "
                            + "association shall be paid by the Owner."));
            doc.add(clause(body,
                    "14. That the Owner will keep the Tenant free and harmless from any "
                            + "claims, proceedings, demands, or actions by others with respect "
                            + "to quiet possession of the premises."));
            doc.add(clause(body,
                    "15. That this Rent Agreement can be terminated before the expiry of "
                            + "this tenancy period by serving One month prior notice in writing "
                            + "by either party."));
            doc.add(clause(body,
                    "16. The Tenant shall maintain the Demised Premises in good and tenable "
                            + "condition and all the minor repairs such as leakage in the "
                            + "sanitary fittings, water taps and electrical usage etc. shall "
                            + "be carried out by the Tenant. That it shall be the responsibility "
                            + "of the Tenant to hand over the vacant and peaceful possession of "
                            + "the demised premises on expiry of the Rent period, or on its "
                            + "early termination, as stated hereinabove in the same condition "
                            + "subject to natural wear and tear."));
            doc.add(clause(body,
                    "17. That in case, where the Premises are not vacated by the Tenant, at "
                            + "the termination of the Rent period, the Tenant will pay damages "
                            + "calculated at two times the rent for any period of occupation "
                            + "commencing from the expiry of the Rent period. The payment of "
                            + "damages as aforesaid will not preclude the Owner from initiating "
                            + "legal proceedings against the Tenant for recovering possession "
                            + "of premises or for any other purpose."));
            doc.add(clause(body,
                    "18. That both the parties shall observe and adhere to the terms and "
                            + "conditions contained hereinabove."));
            doc.add(clause(body,
                    "19. That the Tenant and Owners represent and warrant that they are fully "
                            + "empowered and competent to make this Rent. That both the parties "
                            + "have read over and understood all the contents of this agreement "
                            + "and have signed the same without any force or pressure from any "
                            + "side."));
            String city = building != null && building.buildingCity() != null
                    && !building.buildingCity().isBlank()
                    ? building.buildingCity()
                    : BLANK;
            doc.add(clause(body,
                    "20. In case of any dispute to this agreement and the clauses herein, "
                            + "the same will be settled in the jurisdiction of the " + city
                            + " civil courts."));
            doc.add(clause(body,
                    "21. That the Rent Agreement will be registered in front of the Registrar "
                            + "and the charges towards stamp duty, court fee & lawyer/coordinator "
                            + "will be equally borne by the Owner and Tenant."));

            // ---------- ANNEXURE-I ----------
            doc.add(spacer());
            Paragraph annexure = new Paragraph("ANNEXURE-I", sectionFont);
            annexure.setAlignment(Element.ALIGN_CENTER);
            doc.add(annexure);
            doc.add(spacer());
            String bedroomCount = flat != null && flat.bedrooms() != null
                    ? flat.bedrooms().toString()
                    : BLANK;
            doc.add(new Paragraph(
                    "The " + premisesAddr + " of the Property, consisting "
                            + bedroomCount + " bedrooms, living room, family lounge, kitchen, "
                            + "servant room and inbuilt fittings & fixtures and inventory of "
                            + BLANK + " (Fans), " + BLANK + " (CFL Lights), " + BLANK
                            + " (Geysers), " + BLANK + " (Mirrors).",
                    body));
            doc.add(spacer());

            // ---------- WITNESS / SIGNATURE BLOCK ----------
            doc.add(new Paragraph(
                    "IN WITNESS WHEREOF BOTH PARTIES AGREES AND SIGNS THIS AGREEMENT ON THIS "
                            + "DAY AND YEAR",
                    sectionFont));
            doc.add(spacer());
            doc.add(new Paragraph("WITNESSES:", sectionFont));
            doc.add(new Paragraph("1. Name: " + BLANK, body));
            doc.add(new Paragraph("   Signature: " + BLANK, body));
            doc.add(new Paragraph("   Address: " + LONG_BLANK, body));
            doc.add(spacer());
            doc.add(new Paragraph("2. Name: " + BLANK, body));
            doc.add(new Paragraph("   Signature: " + BLANK, body));
            doc.add(new Paragraph("   Address: " + LONG_BLANK, body));
            doc.add(spacer());

            doc.add(new Paragraph(
                    "Signature of the Owner: " + BLANK
                            + "        Signature of the Tenant: " + BLANK,
                    body));
            doc.add(new Paragraph(
                    "Name of the Owner: " + ownerName
                            + "          Name of the Tenant: " + tenantName,
                    body));
            doc.add(new Paragraph("        OWNER                                 TENANT", sectionFont));
            doc.add(spacer());

            // Digital signature status — ledger note, not a substitute for wet ink.
            doc.add(new Paragraph(
                    "Platform digital signature status: "
                            + nullSafe(lease.getDigitalSignatureStatus()),
                    italic));

            // ---------- NOTARIZATION & RE-UPLOAD NOTICE ----------
            doc.add(spacer());
            doc.add(spacer());
            Paragraph noticeHeader = new Paragraph(
                    "IMPORTANT — NOTARIZATION & UPLOAD INSTRUCTIONS",
                    sectionFont);
            noticeHeader.setAlignment(Element.ALIGN_CENTER);
            doc.add(noticeHeader);
            doc.add(new Paragraph(
                    "This rental agreement is not legally binding until the following steps "
                            + "are completed offline:",
                    body));
            doc.add(new Paragraph(
                    "  1. Print this document on stamp paper of the value prescribed by your "
                            + "state.", body));
            doc.add(new Paragraph(
                    "  2. Both the Owner and the Tenant must sign each page in the presence "
                            + "of two witnesses, who must also sign and provide their address.",
                    body));
            doc.add(new Paragraph(
                    "  3. Get the signed agreement attested and stamped by a Notary Public / "
                            + "Sub-Registrar in the jurisdiction of the property.", body));
            doc.add(new Paragraph(
                    "  4. Scan the fully signed and notary-stamped copy and upload it back "
                            + "to the RentGenius portal under \"My Lease → Upload signed deed\". "
                            + "The lease will be marked legally executed once the uploaded copy "
                            + "is verified.", body));
            doc.add(spacer());
            doc.add(new Paragraph(
                    "Generated by the RentGenius platform on " + PRETTY_DATE.format(LocalDate.now())
                            + ". Lease reference: " + nullSafe(lease.getLeaseNumber())
                            + (reraStamp != null && !reraStamp.isBlank()
                                    ? " · RERA: " + reraStamp
                                    : "")
                            + ".",
                    italic));

            doc.close();
            log.info("Generated lease deed PDF: {}", file);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new LeaseDeedException("Failed to write lease deed PDF: " + file, e);
        }
    }

    /* -------------------------- helpers -------------------------- */

    private Paragraph spacer() {
        return new Paragraph(" ");
    }

    private Paragraph clause(Font body, String text) {
        Paragraph p = new Paragraph(text, body);
        p.setSpacingAfter(6f);
        return p;
    }

    private String formatPremisesAddress(BuildingSummary building, FlatSummary flat) {
        if (building == null && flat == null) {
            return LONG_BLANK;
        }
        StringBuilder sb = new StringBuilder();
        if (flat != null && flat.flatNumber() != null) {
            sb.append("Flat ").append(flat.flatNumber());
            if (flat.floor() != null) {
                sb.append(" (Floor ").append(flat.floor()).append(")");
            }
            sb.append(", ");
        }
        if (building != null) {
            String addr = joinNonBlank(", ",
                    building.buildingName(),
                    building.buildingAddress(),
                    building.buildingCity(),
                    building.buildingState());
            sb.append(addr == null || addr.isBlank() ? LONG_BLANK : addr);
        } else {
            sb.append(LONG_BLANK);
        }
        return sb.toString();
    }

    private String joinNonBlank(String sep, String... parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
