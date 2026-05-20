package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.AgreementResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Agreement;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.client.UserClient.UserSummary;
import com.spa.home_rental_application.property_service.property_service.repository.AgreementRepo;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.AgreementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Slf4j
public class AgreementServiceImpl implements AgreementService {

    private final AgreementRepo agreementRepo;
    private final BuildingRepo buildingRepo;
    private final FlatRepo flatRepo;
    private final UserClient userClient;
    private final AgreementPdfGenerator pdfGenerator;

    /**
     * Where notarized PDFs uploaded by tenants/owners land. Separate dir
     * from the auto-generated deeds so the original is preserved and
     * reviewers can compare.
     */
    @Value("${app.agreements.signed-deed-storage-dir:uploads/lease-deeds-property-signed}")
    private String signedDeedStorageDir;

    /** Hard cap on uploaded notarized PDFs — same shape as KYC/profile uploads. */
    private static final long MAX_SIGNED_DEED_BYTES = 10L * 1024L * 1024L;

    public AgreementServiceImpl(AgreementRepo agreementRepo,
                                BuildingRepo buildingRepo,
                                FlatRepo flatRepo,
                                UserClient userClient,
                                AgreementPdfGenerator pdfGenerator) {
        this.agreementRepo = agreementRepo;
        this.buildingRepo = buildingRepo;
        this.flatRepo = flatRepo;
        this.userClient = userClient;
        this.pdfGenerator = pdfGenerator;
    }

    @Override
    @Transactional
    public AgreementResponseDTO createForAssignment(Flat flat) {
        // Idempotency: if there's already a PENDING_SIGNATURE agreement for
        // this flat AND it belongs to the same tenant, reuse it instead of
        // spawning a duplicate row. This is what causes the "lease shown
        // twice in tenant login" bug — assignFlat called twice (UI retry,
        // double-click, restart-driven retry on a Kafka producer) used to
        // mint a fresh AGR-… UUID each time, leaving stale rows behind.
        Agreement existing = agreementRepo
                .findFirstByFlatIdAndStatusOrderByCreatedAtDesc(
                        flat.getId(), Agreement.Status.PENDING_SIGNATURE)
                .filter(a -> flat.getTenantId() != null
                        && flat.getTenantId().equals(a.getTenantId()))
                .orElse(null);
        if (existing != null) {
            log.info("Reusing pending agreement {} for flat={} tenant={}",
                    existing.getId(), flat.getId(), flat.getTenantId());
            return toDto(existing);
        }

        Building b = buildingRepo.findById(flat.getBuildingId()).orElse(null);
        // Resolve owner + tenant names so the deed reads "John Doe"
        // instead of "Owner ID: AUTH-4". safeFetchUser handles the
        // null-id and Feign-failure paths internally — returning an
        // empty summary that renderDefaultTerms then formats with a
        // sensible fallback ("(name not on file)").
        UserSummary ownerSummary = b != null ? safeFetchUser(b.getOwnerId()) : null;
        UserSummary tenantSummary = safeFetchUser(flat.getTenantId());
        String terms = renderDefaultTerms(flat, b, ownerSummary, tenantSummary);
        LocalDateTime now = LocalDateTime.now();

        Agreement a = Agreement.builder()
                .id("AGR-" + UUID.randomUUID())
                .flatId(flat.getId())
                .buildingId(flat.getBuildingId())
                .tenantId(flat.getTenantId())
                .ownerId(b != null ? b.getOwnerId() : null)
                .rentAmount(flat.getRentAmount())
                .leaseStartDate(flat.getLeaseStartDate())
                .leaseEndDate(flat.getLeaseEndDate())
                .terms(terms)
                .status(Agreement.Status.PENDING_SIGNATURE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Agreement saved = agreementRepo.save(a);
        log.info("Agreement {} created for flat={} tenant={}", saved.getId(), flat.getId(), flat.getTenantId());
        return toDto(saved);
    }

    @Override
    public AgreementResponseDTO getById(String agreementId) {
        return toDto(load(agreementId));
    }

    @Override
    public List<AgreementResponseDTO> getForTenant(String tenantId) {
        return agreementRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toDto).toList();
    }

    @Override
    public List<AgreementResponseDTO> getForOwner(String ownerId) {
        return agreementRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::toDto).toList();
    }

    @Override
    public List<AgreementResponseDTO> getForFlat(String flatId) {
        return agreementRepo.findByFlatIdOrderByCreatedAtDesc(flatId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public AgreementResponseDTO sign(String agreementId, String signatureBase64) {
        Agreement a = load(agreementId);
        if (a.getStatus() != Agreement.Status.PENDING_SIGNATURE) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Agreement " + agreementId + " is " + a.getStatus() + " and cannot be signed.");
        }
        a.setSignatureData(signatureBase64);
        a.setStatus(Agreement.Status.SIGNED);
        a.setSignedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        // Render the deed PDF best-effort. If rendering fails (bad signature
        // bytes, disk full, …) we still mark the lease SIGNED — the PDF can
        // be regenerated later via re-sign or a backfill job.
        try {
            Flat flat = flatRepo.findById(a.getFlatId()).orElse(null);
            Building building = buildingRepo.findById(a.getBuildingId()).orElse(null);
            UserSummary owner = safeFetchUser(a.getOwnerId());
            UserSummary tenant = safeFetchUser(a.getTenantId());
            String path = pdfGenerator.render(a, building, flat, owner, tenant);
            a.setDocumentPath(path);
        } catch (Exception ex) {
            log.warn("Deed PDF render failed for agreement={} — proceeding without doc",
                    agreementId, ex);
        }
        return toDto(agreementRepo.save(a));
    }

    /**
     * Read the rendered PDF bytes off disk.
     *
     * <p>We <b>always render fresh</b> here rather than serving a cached
     * file from a previous request. Reasons:
     * <ul>
     *   <li>The deed template + party-detail enrichment can change between
     *       deploys; a cached PDF would silently serve the old layout.</li>
     *   <li>Owner / tenant KYC details (name, address) can be updated in
     *       user-service after signing; the deed should reflect the
     *       current state.</li>
     *   <li>Render cost is ~50-100 ms for a single-page A4 PDF — well
     *       under any reasonable download latency budget.</li>
     * </ul>
     * The persisted {@code documentPath} is still updated so other code
     * paths (e.g. the {@code hasDocument} DTO flag) stay accurate.
     */
    @Override
    @Transactional
    public byte[] loadDocument(String agreementId) throws IOException {
        Agreement a = load(agreementId);
        String path;
        try {
            Flat flat = flatRepo.findById(a.getFlatId()).orElse(null);
            Building building = buildingRepo.findById(a.getBuildingId()).orElse(null);
            UserSummary owner = safeFetchUser(a.getOwnerId());
            UserSummary tenant = safeFetchUser(a.getTenantId());
            path = pdfGenerator.render(a, building, flat, owner, tenant);
            a.setDocumentPath(path);
            a.setUpdatedAt(LocalDateTime.now());
            agreementRepo.save(a);
            log.info("Rendered deed PDF on-demand for agreement={} (path={})",
                    agreementId, path);
        } catch (Exception ex) {
            log.error("On-demand deed render failed for agreement={}",
                    agreementId, ex);
            throw new ResponseStatusException(NOT_FOUND,
                    "Agreement " + agreementId + " deed is unavailable: "
                            + ex.getMessage());
        }
        return Files.readAllBytes(Paths.get(path));
    }

    @Override
    @Transactional
    public AgreementResponseDTO reject(String agreementId, String reason) {
        Agreement a = load(agreementId);
        if (a.getStatus() != Agreement.Status.PENDING_SIGNATURE) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Agreement " + agreementId + " is " + a.getStatus() + " and cannot be rejected.");
        }
        a.setStatus(Agreement.Status.REJECTED);
        a.setRejectionReason(reason);
        a.setRejectedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return toDto(agreementRepo.save(a));
    }

    /**
     * Receive the wet-signed, notary-stamped PDF that the parties uploaded
     * back to the platform after the offline notarization flow. The
     * agreement must already be SIGNED — uploading on a PENDING_SIGNATURE
     * or REJECTED row is rejected as 400.
     *
     * <p>The original auto-generated deed at {@code documentPath} is kept
     * so reviewers (and disputes later) can compare against the wet-signed
     * copy.
     */
    @Override
    @Transactional
    public AgreementResponseDTO uploadSignedDeed(String agreementId, MultipartFile file)
            throws IOException {
        Agreement a = load(agreementId);
        if (a.getStatus() != Agreement.Status.SIGNED) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Agreement " + agreementId + " is " + a.getStatus()
                            + " — only SIGNED agreements accept a notarized upload.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file is empty.");
        }
        if (file.getSize() > MAX_SIGNED_DEED_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Uploaded file exceeds 10 MB limit (" + file.getSize() + " bytes).");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Uploaded file must be a PDF (got " + contentType + ").");
        }

        Path dir = Paths.get(signedDeedStorageDir);
        Files.createDirectories(dir);
        Path dest = dir.resolve(a.getId() + ".pdf");
        try (var in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        a.setSignedDeedPath(dest.toAbsolutePath().toString());
        a.setNotarizedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        Agreement saved = agreementRepo.save(a);
        log.info("Stored signed deed for agreement={} at {}", agreementId, dest);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] loadSignedDeed(String agreementId) throws IOException {
        Agreement a = load(agreementId);
        String path = a.getSignedDeedPath();
        if (path == null || path.isBlank() || !Files.exists(Paths.get(path))) {
            throw new ResponseStatusException(NOT_FOUND,
                    "No notarized deed has been uploaded for agreement " + agreementId);
        }
        return Files.readAllBytes(Paths.get(path));
    }

    /* -------------------------- helpers -------------------------- */

    /**
     * Best-effort KYC fetch — never propagates an exception. Feign already
     * has a fallback bean wired in, but we still wrap defensively so a
     * {@code null} userId or DTO-level failure doesn't take down the
     * deed-rendering path.
     */
    private UserSummary safeFetchUser(String userId) {
        if (userId == null || userId.isBlank()) return null;
        // Agreement.ownerId / tenantId are auth-service user ids
        // (sourced from Building.ownerId and Flat.tenantId, both of
        // which carry the authUserId — not the user-service surrogate
        // id). Use the by-auth-id endpoint so the lookup actually
        // hits a row. Previously this called getUserById(userId)
        // which 404'd silently and made every owner/tenant name
        // render as blank → the lease text fell back to raw IDs.
        try {
            UserSummary u = userClient.getUserByAuthId(userId);
            return u == null ? UserSummary.empty() : u;
        } catch (Exception ex) {
            log.warn("user-service lookup failed for authUserId={} — falling back to blanks: {}",
                    userId, ex.getMessage());
            return UserSummary.empty();
        }
    }

    private Agreement load(String id) {
        return agreementRepo.findById(id).orElseThrow(
                () -> new RecordNotFoundException("Agreement not found: " + id));
    }

    private String renderDefaultTerms(Flat flat, Building b,
                                      UserSummary owner, UserSummary tenant) {
        // Resolve display names. fullName() returns null when the
        // user-service row exists but has no first/last name; fall
        // through to a sentinel so the deed never renders an empty
        // line. The auth-id is added in parens for audit /
        // reconciliation purposes — the deed is a legal document and
        // someone reading it weeks later may need to map back to a
        // user record.
        // Names are the human-readable identity in the deed. Internal
        // IDs are deliberately omitted — the renter shouldn't see raw
        // UUIDs in their lease agreement. The ID is still on the
        // Agreement entity and AgreementResponseDTO for any
        // audit/reconciliation flow, just not in the printed terms.
        String ownerName = (owner != null && owner.fullName() != null)
                ? owner.fullName()
                : "(name on file)";
        String tenantName = (tenant != null && tenant.fullName() != null)
                ? tenant.fullName()
                : "(name on file)";

        StringBuilder t = new StringBuilder(2048);
        t.append("LEASE AGREEMENT\n\n");
        t.append("1. PARTIES\n");
        t.append("   Owner: ").append(ownerName).append("\n");
        t.append("   Tenant: ").append(tenantName).append("\n\n");
        t.append("2. PROPERTY\n");
        t.append("   Building: ").append(b != null ? b.getBuildingName() : flat.getBuildingId()).append("\n");
        t.append("   Address: ").append(b != null ? b.getBuildingAddress() : "—").append("\n");
        t.append("   Flat: ").append(flat.getFlatNumber())
                .append(", ").append(floorWord(flat.getFloor())).append(" floor\n\n");
        t.append("3. RENT\n");
        t.append("   Monthly rent: Rs. ").append(flat.getRentAmount()).append("\n");
        t.append("   Due date: 5th of every month.\n\n");
        t.append("4. TERM\n");
        t.append("   Lease starts: ").append(flat.getLeaseStartDate()).append("\n");
        t.append("   Lease ends:   ").append(flat.getLeaseEndDate()).append("\n");
        t.append("   Notice period for vacating: 2 months minimum.\n\n");
        t.append("5. UTILITIES & MAINTENANCE\n");
        t.append("   Tenant pays: electricity, water, internet (unless owner agrees otherwise).\n");
        t.append("   Owner is responsible for structural maintenance via the platform.\n\n");
        t.append("6. SECURITY DEPOSIT\n");
        // Issue #6: deposit is now NON-refundable on termination. The
        // owner retains the entire amount to cover wear-and-tear,
        // outstanding dues, and any damages caused during occupancy.
        // The amount is THREE months' rent — matches the
        // property-detail page's deposit display (rent × 3).
        t.append("   Equal to three months' rent, payable at the time of signing this agreement.\n");
        t.append("   The security deposit is NON-REFUNDABLE — the Owner shall retain the full\n");
        t.append("   amount upon expiry or early termination of this lease, in lieu of\n");
        t.append("   wear-and-tear adjustments, outstanding dues, and any damages caused by\n");
        t.append("   the Tenant.\n\n");
        t.append("By signing this agreement the tenant confirms they have read and accepted these terms.");
        return t.toString();
    }

    /**
     * Owner enters a floor number; the deed prints the English
     * ordinal ("Ground", "First", … "Twentieth"). Beyond 20 falls
     * back to the numeric ordinal ("21st", "22nd") so the line
     * still reads naturally. Mirrors the frontend
     * {@code floorLabel()} helper on lib/utils.ts.
     */
    private static final String[] FLOOR_WORDS = {
            "Ground", "First", "Second", "Third", "Fourth", "Fifth",
            "Sixth", "Seventh", "Eighth", "Ninth", "Tenth",
            "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth",
            "Sixteenth", "Seventeenth", "Eighteenth", "Nineteenth", "Twentieth",
    };

    private static String floorWord(Integer floor) {
        if (floor == null) return "—";
        int n = floor;
        if (n < 0) {
            int abs = Math.abs(n);
            return abs == 1 ? "Basement" : "Basement " + abs;
        }
        if (n < FLOOR_WORDS.length) return FLOOR_WORDS[n];
        int lastTwo = n % 100;
        int lastOne = n % 10;
        String suffix;
        if (lastTwo >= 11 && lastTwo <= 13) suffix = "th";
        else if (lastOne == 1) suffix = "st";
        else if (lastOne == 2) suffix = "nd";
        else if (lastOne == 3) suffix = "rd";
        else suffix = "th";
        return n + suffix;
    }


    /**
     * Resolve human-readable owner/tenant names from user-service so the
     * lease card no longer leaks raw UUIDs (Issue #5). Failure is silently
     * absorbed — names are nice-to-have, not load-bearing for the DTO.
     *
     * <p><b>Terms are re-rendered fresh on every read</b> rather than
     * returning the persisted {@code Agreement.terms} field. Reasons:
     * <ul>
     *   <li>The persisted text is a snapshot from the moment the
     *       agreement was created. Older agreements still carry the
     *       legacy "Owner ID: 4 / Tenant ID: 5" copy and the
     *       "two months' deposit" wording.</li>
     *   <li>The template (parties, address, deposit clause) is
     *       legitimately platform-policy, not contract-binding text
     *       — the contract-binding bits are the dates + rent + flat,
     *       which come from the same flat/building rows and can't
     *       change behind the parties' back.</li>
     *   <li>Re-rendering is cheap (single string concat) and a
     *       safety net so policy fixes (deposit wording, name
     *       format) actually reach existing leases without a
     *       backfill migration.</li>
     * </ul>
     * If you ever need contract-frozen text (lease law sometimes
     * requires the same wording forever), gate this on
     * {@code a.getStatus() == SIGNED} and only re-render PENDING
     * agreements.
     */
    private AgreementResponseDTO toDto(Agreement a) {
        UserSummary ownerSummary = null;
        UserSummary tenantSummary = null;
        try {
            tenantSummary = safeFetchUser(a.getTenantId());
        } catch (Exception ignored) { /* names are best-effort */ }
        try {
            ownerSummary = safeFetchUser(a.getOwnerId());
        } catch (Exception ignored) { /* names are best-effort */ }
        String tenantName = (tenantSummary != null) ? tenantSummary.fullName() : null;
        String ownerName = (ownerSummary != null) ? ownerSummary.fullName() : null;

        // Re-render the terms on every read so policy fixes (name
        // format, deposit wording, floor word) reach existing leases
        // without a backfill. Falls back to the persisted terms when
        // the flat / building row can't be loaded (e.g. flat was
        // hard-deleted) — better stale text than a 500.
        String terms = a.getTerms();
        try {
            Flat flat = (a.getFlatId() != null)
                    ? flatRepo.findById(a.getFlatId()).orElse(null)
                    : null;
            Building b = (a.getBuildingId() != null)
                    ? buildingRepo.findById(a.getBuildingId()).orElse(null)
                    : null;
            if (flat != null) {
                terms = renderDefaultTerms(flat, b, ownerSummary, tenantSummary);
            }
        } catch (Exception ex) {
            log.warn("Terms re-render failed for agreement={} — falling back to stored copy: {}",
                    a.getId(), ex.getMessage());
        }

        return new AgreementResponseDTO(
                a.getId(), a.getFlatId(), a.getBuildingId(), a.getTenantId(), a.getOwnerId(),
                tenantName, ownerName,
                a.getRentAmount(), a.getLeaseStartDate(), a.getLeaseEndDate(),
                terms, a.getStatus().name(), a.getSignatureData(),
                a.getSignedAt(), a.getRejectedAt(), a.getRejectionReason(),
                a.getDocumentPath() != null,
                a.getSignedDeedPath() != null,
                a.getNotarizedAt(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
