package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.AgreementResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Agreement;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.AgreementRepo;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.service.AgreementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    private final AgreementPdfGenerator pdfGenerator;

    public AgreementServiceImpl(AgreementRepo agreementRepo,
                                BuildingRepo buildingRepo,
                                AgreementPdfGenerator pdfGenerator) {
        this.agreementRepo = agreementRepo;
        this.buildingRepo = buildingRepo;
        this.pdfGenerator = pdfGenerator;
    }

    @Override
    @Transactional
    public AgreementResponseDTO createForAssignment(Flat flat) {
        Building b = buildingRepo.findById(flat.getBuildingId()).orElse(null);
        String terms = renderDefaultTerms(flat, b);
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
            String path = pdfGenerator.render(a);
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
     * <p><b>Self-heal:</b> if {@code documentPath} is null (sign happened
     * before the PDF generator was wired in, or render failed at sign
     * time) <em>or</em> the file on disk is missing (manual cleanup,
     * container restart with ephemeral storage), we render the PDF
     * on-the-fly here. The new path is persisted so subsequent calls hit
     * the cache. Tenants and owners can therefore download the deed at
     * any point in the lifecycle — including draft / pending-signature.
     */
    @Override
    @Transactional
    public byte[] loadDocument(String agreementId) throws IOException {
        Agreement a = load(agreementId);
        String path = a.getDocumentPath();

        boolean needsRender = path == null
                || path.isBlank()
                || !Files.exists(Paths.get(path));
        if (needsRender) {
            try {
                String fresh = pdfGenerator.render(a);
                a.setDocumentPath(fresh);
                a.setUpdatedAt(LocalDateTime.now());
                agreementRepo.save(a);
                path = fresh;
                log.info("Rendered deed PDF on-demand for agreement={} (path={})",
                        agreementId, fresh);
            } catch (Exception ex) {
                log.error("On-demand deed render failed for agreement={}",
                        agreementId, ex);
                throw new ResponseStatusException(NOT_FOUND,
                        "Agreement " + agreementId + " deed is unavailable: "
                                + ex.getMessage());
            }
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

    /* -------------------------- helpers -------------------------- */

    private Agreement load(String id) {
        return agreementRepo.findById(id).orElseThrow(
                () -> new RecordNotFoundException("Agreement not found: " + id));
    }

    private String renderDefaultTerms(Flat flat, Building b) {
        StringBuilder t = new StringBuilder(2048);
        t.append("LEASE AGREEMENT\n\n");
        t.append("1. PARTIES\n");
        t.append("   Owner ID: ").append(b != null ? b.getOwnerId() : "—").append("\n");
        t.append("   Tenant ID: ").append(flat.getTenantId()).append("\n\n");
        t.append("2. PROPERTY\n");
        t.append("   Building: ").append(b != null ? b.getBuildingName() : flat.getBuildingId()).append("\n");
        t.append("   Address: ").append(b != null ? b.getBuildingAddress() : "—").append("\n");
        t.append("   Flat: ").append(flat.getFlatNumber())
                .append(" (Floor ").append(flat.getFloor()).append(")\n\n");
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
        t.append("   Equal to two months' rent, refundable on lease termination subject to inspection.\n\n");
        t.append("By signing this agreement the tenant confirms they have read and accepted these terms.");
        return t.toString();
    }

    private AgreementResponseDTO toDto(Agreement a) {
        return new AgreementResponseDTO(
                a.getId(), a.getFlatId(), a.getBuildingId(), a.getTenantId(), a.getOwnerId(),
                a.getRentAmount(), a.getLeaseStartDate(), a.getLeaseEndDate(),
                a.getTerms(), a.getStatus().name(), a.getSignatureData(),
                a.getSignedAt(), a.getRejectedAt(), a.getRejectionReason(),
                a.getDocumentPath() != null,
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
