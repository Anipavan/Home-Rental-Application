package com.spa.home_rental_application.lease_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseSignedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseTerminatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.LeaseServiceEvents;
import com.spa.home_rental_application.lease_service.DTO.Request.CreateLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.RenewLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.SignLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.TerminateLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseHistoryDto;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseResponseDto;
import com.spa.home_rental_application.lease_service.Entities.Lease;
import com.spa.home_rental_application.lease_service.Entities.LeaseHistory;
import com.spa.home_rental_application.lease_service.Exceptionclass.InvalidLeaseStateException;
import com.spa.home_rental_application.lease_service.Exceptionclass.LeaseNotFoundException;
import com.spa.home_rental_application.lease_service.client.ComplianceClient;
import com.spa.home_rental_application.lease_service.config.LeaseProperties;
import com.spa.home_rental_application.lease_service.mapper.LeaseMapper;
import com.spa.home_rental_application.lease_service.repository.LeaseHistoryRepository;
import com.spa.home_rental_application.lease_service.repository.LeaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class LeaseServiceImpl implements LeaseService {

    private final LeaseRepository leaseRepository;
    private final LeaseHistoryRepository historyRepository;
    private final LeaseMapper mapper;
    private final LeaseDeedPdfGenerator pdfGenerator;
    private final ComplianceClient complianceClient;
    private final LeaseServiceEvents events;
    private final LeaseProperties props;

    public LeaseServiceImpl(LeaseRepository leaseRepository,
                            LeaseHistoryRepository historyRepository,
                            LeaseMapper mapper,
                            LeaseDeedPdfGenerator pdfGenerator,
                            ComplianceClient complianceClient,
                            LeaseServiceEvents events,
                            LeaseProperties props) {
        this.leaseRepository = leaseRepository;
        this.historyRepository = historyRepository;
        this.mapper = mapper;
        this.pdfGenerator = pdfGenerator;
        this.complianceClient = complianceClient;
        this.events = events;
        this.props = props;
    }

    // ---------- Public API ----------

    @Override
    @Transactional
    public LeaseResponseDto create(CreateLeaseRequest req) {
        log.info("Create lease tenantId={} flatId={} term={}→{}",
                req.tenantId(), req.flatId(), req.startDate(), req.endDate());

        if (!req.endDate().isAfter(req.startDate())) {
            throw new InvalidLeaseStateException("endDate must be after startDate", "INVALID_DATES");
        }

        Lease lease = Lease.builder()
                .tenantId(req.tenantId())
                .flatId(req.flatId())
                .ownerId(req.ownerId())
                .leaseNumber(nextLeaseNumber())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .rentAmount(req.rentAmount())
                .securityDeposit(req.securityDeposit())
                .rentIncrementPercent(req.rentIncrementPercent() != null
                        ? req.rentIncrementPercent() : props.getDefaultRentIncrementPercent())
                .status("DRAFT")
                .digitalSignatureStatus("PENDING")
                .build();

        Lease saved = leaseRepository.save(lease);
        recordHistory(saved.getId(), "CREATED", null, saved.getRentAmount(),
                saved.getOwnerId(), "Lease draft created");

        // Try to stamp with RERA up-front (best-effort; fallback covers outage)
        if (req.state() != null && !req.state().isBlank()) {
            try {
                String stamp = complianceClient.generateReraMetadata(saved.getId(),
                        new ComplianceClient.GenerateReraLeaseDto(saved.getId(), saved.getFlatId(), req.state()))
                        .get("reraMetadata");
                saved.setReraAgreementNumber(stamp);
                saved = leaseRepository.save(saved);
            } catch (Exception ex) {
                log.warn("RERA stamp lookup failed for leaseId={} — continuing without stamp",
                        saved.getId(), ex);
            }
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaseResponseDto getById(String id) {
        return leaseRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new LeaseNotFoundException("No lease with id=" + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponseDto> getByTenantId(String tenantId) {
        return leaseRepository.findByTenantId(tenantId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponseDto> getByFlatId(String flatId) {
        return leaseRepository.findByFlatId(flatId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public LeaseResponseDto renew(String leaseId, RenewLeaseRequest req) {
        Lease lease = mustFind(leaseId);
        if (!"ACTIVE".equals(lease.getStatus()) && !"EXPIRED".equals(lease.getStatus())) {
            throw new InvalidLeaseStateException(
                    "Lease " + leaseId + " in status " + lease.getStatus() + " cannot be renewed");
        }
        if (!req.newEndDate().isAfter(lease.getEndDate())) {
            throw new InvalidLeaseStateException(
                    "newEndDate must be after current endDate=" + lease.getEndDate(),
                    "INVALID_RENEWAL_DATE");
        }
        var prevEnd = lease.getEndDate();
        var prevRent = lease.getRentAmount();

        lease.setEndDate(req.newEndDate());
        if (req.newRent() != null) {
            lease.setRentAmount(req.newRent());
        }
        lease.setStatus("ACTIVE");
        lease.setExpiryWarningSentAt(null);
        Lease saved = leaseRepository.save(lease);
        recordHistory(saved.getId(), "RENEWED", prevRent, saved.getRentAmount(),
                saved.getOwnerId(), req.notes());

        events.sendLeaseRenewed(LeaseRenewedEvent.builder()
                .eventType("lease.renewed")
                .leaseId(saved.getId())
                .tenantId(saved.getTenantId())
                .flatId(saved.getFlatId())
                .ownerId(saved.getOwnerId())
                .previousEndDate(prevEnd)
                .newEndDate(saved.getEndDate())
                .previousRent(prevRent)
                .newRent(saved.getRentAmount())
                .renewedAt(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LeaseResponseDto terminate(String leaseId, TerminateLeaseRequest req) {
        Lease lease = mustFind(leaseId);
        if ("TERMINATED".equals(lease.getStatus())) {
            throw new InvalidLeaseStateException("Lease " + leaseId + " is already TERMINATED");
        }
        LocalDate when = req.terminationDate() != null ? req.terminationDate() : LocalDate.now();
        lease.setStatus("TERMINATED");
        lease.setTerminatedAt(LocalDateTime.now());
        lease.setTerminationReason(req.terminationReason());
        Lease saved = leaseRepository.save(lease);
        recordHistory(saved.getId(), "TERMINATED",
                saved.getRentAmount(), saved.getRentAmount(),
                saved.getOwnerId(), req.notes());

        events.sendLeaseTerminated(LeaseTerminatedEvent.builder()
                .eventType("lease.terminated")
                .leaseId(saved.getId())
                .tenantId(saved.getTenantId())
                .flatId(saved.getFlatId())
                .ownerId(saved.getOwnerId())
                .terminationReason(req.terminationReason())
                .terminatedOn(when)
                .timestamp(LocalDateTime.now())
                .build());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LeaseResponseDto sign(String leaseId, SignLeaseRequest req) {
        Lease lease = mustFind(leaseId);
        if (!"DRAFT".equals(lease.getStatus())) {
            throw new InvalidLeaseStateException(
                    "Only DRAFT leases can be signed; current status=" + lease.getStatus());
        }
        lease.setDigitalSignatureStatus("SIGNED");
        lease.setStatus("ACTIVE");
        Lease saved = leaseRepository.save(lease);
        recordHistory(saved.getId(), "SIGNED", null, saved.getRentAmount(),
                req.signedBy(), "Signed via " + req.signatureProvider());

        events.sendLeaseSigned(LeaseSignedEvent.builder()
                .eventType("lease.signed")
                .leaseId(saved.getId())
                .leaseNumber(saved.getLeaseNumber())
                .tenantId(saved.getTenantId())
                .flatId(saved.getFlatId())
                .ownerId(saved.getOwnerId())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .rentAmount(saved.getRentAmount())
                .securityDeposit(saved.getSecurityDeposit())
                .timestamp(LocalDateTime.now())
                .build());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LeaseResponseDto generateReraStampedLease(String leaseId, String state) {
        Lease lease = mustFind(leaseId);
        String stamp = complianceClient.generateReraMetadata(leaseId,
                new ComplianceClient.GenerateReraLeaseDto(leaseId, lease.getFlatId(), state))
                .get("reraMetadata");
        lease.setReraAgreementNumber(stamp);
        String pdfPath = pdfGenerator.generate(lease, stamp);
        lease.setDocumentUrl(pdfPath);
        Lease saved = leaseRepository.save(lease);
        recordHistory(saved.getId(), "AMENDED", saved.getRentAmount(), saved.getRentAmount(),
                saved.getOwnerId(), "RERA stamp generated for state=" + state);
        return mapper.toResponse(saved);
    }

    /**
     * Stream the rendered deed PDF.
     *
     * <p><b>Self-heal:</b> if the lease has no {@code documentUrl} yet (RERA
     * stamp never invoked, signing happened before the generator was wired
     * up, …) <em>or</em> the file on disk is missing, we render the PDF
     * on-the-fly here using whatever stamp is on the row (or "" when none),
     * persist the path, and return the bytes. Means the owner / tenant can
     * always grab the deed even mid-lifecycle, and a stamp can be applied
     * later without breaking the link.
     */
    @Override
    @Transactional
    public byte[] downloadDeed(String leaseId) throws IOException {
        Lease lease = mustFind(leaseId);
        String path = lease.getDocumentUrl();

        boolean needsRender = path == null
                || path.isBlank()
                || !Files.exists(Paths.get(path));
        if (needsRender) {
            try {
                String stamp = lease.getReraAgreementNumber() == null
                        ? ""
                        : lease.getReraAgreementNumber();
                String fresh = pdfGenerator.generate(lease, stamp);
                lease.setDocumentUrl(fresh);
                leaseRepository.save(lease);
                path = fresh;
                log.info("Rendered lease deed on-demand for lease={} (path={})",
                        leaseId, fresh);
            } catch (Exception ex) {
                log.error("On-demand lease deed render failed for {}", leaseId, ex);
                throw new LeaseNotFoundException(
                        "Lease " + leaseId + " deed is unavailable: " + ex.getMessage());
            }
        }
        return Files.readAllBytes(Paths.get(path));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponseDto> getLeasesExpiringWithin(int days) {
        LocalDate today = LocalDate.now();
        return leaseRepository.findExpiringWithoutWarning(today, today.plusDays(days))
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseHistoryDto> getHistory(String leaseId) {
        mustFind(leaseId);
        return historyRepository.findByLeaseIdOrderByChangedAtDesc(leaseId)
                .stream().map(mapper::toHistoryDto).toList();
    }

    // ---------- Helpers ----------

    private Lease mustFind(String leaseId) {
        return leaseRepository.findById(leaseId)
                .orElseThrow(() -> new LeaseNotFoundException("No lease with id=" + leaseId));
    }

    private void recordHistory(String leaseId, String type,
                               java.math.BigDecimal prevRent, java.math.BigDecimal newRent,
                               String changedBy, String notes) {
        historyRepository.save(LeaseHistory.builder()
                .leaseId(leaseId)
                .eventType(type)
                .previousRent(prevRent)
                .newRent(newRent)
                .changedBy(changedBy)
                .notes(notes)
                .build());
    }

    private String nextLeaseNumber() {
        // RG-LEASE-{year}-{8-char short uuid}
        return "RG-LEASE-" + Year.now().getValue() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
