package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AddExpenseRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.SetupSocietyRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MaintenanceExpenseResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyConfigResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyLedgerResponse;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceExpense;
import com.spa.home_rental_application.property_service.property_service.Entities.SocietyConfig;
import com.spa.home_rental_application.property_service.property_service.Mapper.SocietyMapper;
import com.spa.home_rental_application.property_service.property_service.enums.ExpenseCategory;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.MaintenanceExpenseRepository;
import com.spa.home_rental_application.property_service.property_service.repository.SocietyConfigRepository;
import com.spa.home_rental_application.property_service.property_service.security.CallerSecurity;
import com.spa.home_rental_application.property_service.property_service.security.ForbiddenException;
import com.spa.home_rental_application.property_service.property_service.service.SocietyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of the society / common-area maintenance ledger.
 *
 * <p>Authorisation model:
 * <ul>
 *   <li><b>Mutations</b> (setup, update config, add/edit/delete
 *       expense): caller must be the building owner OR the assigned
 *       {@code maintainer_user_id} OR admin.</li>
 *   <li><b>Reads</b> (config, ledger): owner / maintainer / a tenant
 *       living in any flat in the building / admin.</li>
 *   <li><b>Public ledger</b>: no auth — possession of the token IS
 *       the credential. The token is a 32-char URL-safe random
 *       string (~190 bits entropy) and rotatable.</li>
 * </ul>
 *
 * <p>Reads carry zero PII for the public path — the ledger response
 * has no tenant names, no flat numbers, no maintainer name.
 */
@Service
@Slf4j
public class SocietyServiceImpl implements SocietyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SocietyConfigRepository configRepo;
    private final MaintenanceExpenseRepository expenseRepo;
    private final BuildingRepo buildingRepo;
    private final FlatRepo flatRepo;
    private final SocietyMapper mapper;

    public SocietyServiceImpl(SocietyConfigRepository configRepo,
                              MaintenanceExpenseRepository expenseRepo,
                              BuildingRepo buildingRepo,
                              FlatRepo flatRepo,
                              SocietyMapper mapper) {
        this.configRepo = configRepo;
        this.expenseRepo = expenseRepo;
        this.buildingRepo = buildingRepo;
        this.flatRepo = flatRepo;
        this.mapper = mapper;
    }

    // ── Config ────────────────────────────────────────────────────

    @Override
    @Transactional
    public SocietyConfigResponse setupSociety(String buildingId, SetupSocietyRequest req) {
        Building b = requireBuilding(buildingId);
        CallerSecurity.requireOwnerOrAdmin(b.getOwnerId());

        if (configRepo.findByBuildingId(buildingId).isPresent()) {
            throw new IllegalStateException(
                    "Society config already exists for this building — "
                            + "use updateConfig() instead.");
        }

        // Default maintainer to the calling owner if absent/blank
        String maintainerUserId = (req.maintainerUserId() == null
                || req.maintainerUserId().isBlank())
                ? CallerSecurity.getCurrentAuthUserId().orElse(b.getOwnerId())
                : req.maintainerUserId().trim();

        // Default display name to the building name
        String displayName = (req.societyDisplayName() == null
                || req.societyDisplayName().isBlank())
                ? b.getBuildingName()
                : req.societyDisplayName().trim();

        LocalDateTime now = LocalDateTime.now();
        SocietyConfig cfg = SocietyConfig.builder()
                .buildingId(buildingId)
                .monthlyDueDay(req.monthlyDueDay() == null ? 5 : req.monthlyDueDay())
                .defaultPerFlatAmount(req.defaultPerFlatAmount())
                .maintainerUserId(maintainerUserId)
                .publicViewToken(generateToken())
                .societyDisplayName(displayName)
                .createdAt(now)
                .updatedAt(now)
                .build();
        cfg = configRepo.save(cfg);
        log.info("Society setup buildingId={} maintainerUserId={} default={}",
                buildingId, maintainerUserId, req.defaultPerFlatAmount());
        return mapper.toResponse(cfg);
    }

    @Override
    @Transactional(readOnly = true)
    public SocietyConfigResponse getConfig(String buildingId) {
        SocietyConfig cfg = requireConfig(buildingId);
        requireOwnerOrMaintainerOrTenantOrAdmin(buildingId, cfg);
        return mapper.toResponse(cfg);
    }

    @Override
    @Transactional
    public SocietyConfigResponse updateConfig(String buildingId, SetupSocietyRequest req) {
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        requireOwnerOrMaintainerOrAdmin(cfg, b);

        if (req.defaultPerFlatAmount() != null) {
            cfg.setDefaultPerFlatAmount(req.defaultPerFlatAmount());
        }
        if (req.monthlyDueDay() != null) {
            cfg.setMonthlyDueDay(req.monthlyDueDay());
        }
        if (req.societyDisplayName() != null && !req.societyDisplayName().isBlank()) {
            cfg.setSocietyDisplayName(req.societyDisplayName().trim());
        }
        // Only the building owner / admin can reassign the maintainer —
        // a maintainer can't unilaterally hand the role to someone else.
        if (req.maintainerUserId() != null && !req.maintainerUserId().isBlank()) {
            CallerSecurity.requireOwnerOrAdmin(b.getOwnerId());
            cfg.setMaintainerUserId(req.maintainerUserId().trim());
        }
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg = configRepo.save(cfg);
        return mapper.toResponse(cfg);
    }

    @Override
    @Transactional
    public SocietyConfigResponse regeneratePublicToken(String buildingId) {
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        // Only owner / admin can rotate the token — maintainer can't.
        CallerSecurity.requireOwnerOrAdmin(b.getOwnerId());
        cfg.setPublicViewToken(generateToken());
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg = configRepo.save(cfg);
        log.info("Society token rotated buildingId={}", buildingId);
        return mapper.toResponse(cfg);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocietyConfigResponse> listMySocieties() {
        String me = CallerSecurity.getCurrentAuthUserId()
                .orElseThrow(() -> new ForbiddenException("Sign in required."));
        // Two paths: societies where the caller is the owner of the
        // building, and societies where they're the assigned maintainer.
        // Both deduped by building_id.
        List<SocietyConfig> asMaintainer = configRepo.findByMaintainerUserId(me);
        List<Building> myBuildings = buildingRepo.findByOwnerId(me);
        Map<String, SocietyConfig> byBuilding = new LinkedHashMap<>();
        for (SocietyConfig c : asMaintainer) {
            byBuilding.put(c.getBuildingId(), c);
        }
        for (Building b : myBuildings) {
            configRepo.findByBuildingId(b.getBuildingId())
                    .ifPresent(c -> byBuilding.putIfAbsent(c.getBuildingId(), c));
        }
        return byBuilding.values().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SocietyConfigResponse getMyTenantSociety() {
        String me = CallerSecurity.getCurrentAuthUserId()
                .orElseThrow(() -> new ForbiddenException("Sign in required."));
        // A tenant is linked to one flat via flats.tenant_id. From flat
        // → building → societyConfig.
        Optional<Flat> myFlat = flatRepo.findAll().stream()
                .filter(f -> me.equals(f.getTenantId()))
                .findFirst();
        if (myFlat.isEmpty()) return null;
        return configRepo.findByBuildingId(myFlat.get().getBuildingId())
                .map(mapper::toResponse)
                .orElse(null);
    }

    // ── Expenses ──────────────────────────────────────────────────

    @Override
    @Transactional
    public MaintenanceExpenseResponse addExpense(String buildingId, AddExpenseRequest req) {
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        requireOwnerOrMaintainerOrAdmin(cfg, b);
        String me = CallerSecurity.getCurrentAuthUserId().orElse(cfg.getMaintainerUserId());

        LocalDateTime now = LocalDateTime.now();
        MaintenanceExpense e = MaintenanceExpense.builder()
                .buildingId(buildingId)
                .expenseMonth(req.expenseMonth())
                .category(req.category())
                .subcategory(req.subcategory())
                .amount(req.amount())
                .vendorName(req.vendorName())
                .paidOnDate(req.paidOnDate())
                .receiptDocId(req.receiptDocId())
                .notes(req.notes())
                .addedByUserId(me)
                .addedAt(now)
                .updatedAt(now)
                .build();
        e = expenseRepo.save(e);
        log.info("Expense added buildingId={} month={} category={} amount={}",
                buildingId, req.expenseMonth(), req.category(), req.amount());
        return mapper.toResponse(e);
    }

    @Override
    @Transactional
    public MaintenanceExpenseResponse updateExpense(
            String buildingId, String expenseId, AddExpenseRequest req) {
        MaintenanceExpense e = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Expense not found: " + expenseId));
        if (!buildingId.equals(e.getBuildingId())) {
            throw new IllegalArgumentException(
                    "Expense " + expenseId + " does not belong to building " + buildingId);
        }
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        requireOwnerOrMaintainerOrAdmin(cfg, b);

        e.setExpenseMonth(req.expenseMonth());
        e.setCategory(req.category());
        e.setSubcategory(req.subcategory());
        e.setAmount(req.amount());
        e.setVendorName(req.vendorName());
        e.setPaidOnDate(req.paidOnDate());
        e.setReceiptDocId(req.receiptDocId());
        e.setNotes(req.notes());
        e.setUpdatedAt(LocalDateTime.now());
        return mapper.toResponse(expenseRepo.save(e));
    }

    @Override
    @Transactional
    public void deleteExpense(String buildingId, String expenseId) {
        MaintenanceExpense e = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Expense not found: " + expenseId));
        if (!buildingId.equals(e.getBuildingId())) {
            throw new IllegalArgumentException(
                    "Expense does not belong to this building.");
        }
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        requireOwnerOrMaintainerOrAdmin(cfg, b);
        expenseRepo.delete(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceExpenseResponse> listExpenses(String buildingId, String month) {
        SocietyConfig cfg = requireConfig(buildingId);
        requireOwnerOrMaintainerOrTenantOrAdmin(buildingId, cfg);
        List<MaintenanceExpense> rows = (month == null || month.isBlank())
                ? expenseRepo.findByBuildingIdOrderByPaidOnDateDesc(buildingId)
                : expenseRepo.findByBuildingIdAndExpenseMonthOrderByPaidOnDateDesc(buildingId, month);
        return rows.stream().map(mapper::toResponse).toList();
    }

    // ── Ledger ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SocietyLedgerResponse getLedger(String buildingId, String month) {
        SocietyConfig cfg = requireConfig(buildingId);
        requireOwnerOrMaintainerOrTenantOrAdmin(buildingId, cfg);
        return buildLedger(cfg, month);
    }

    @Override
    @Transactional(readOnly = true)
    public SocietyLedgerResponse getPublicLedger(String token, String month) {
        SocietyConfig cfg = configRepo.findByPublicViewToken(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired ledger link."));
        // No auth check — possessing the token IS the credential.
        return buildLedger(cfg, month);
    }

    private SocietyLedgerResponse buildLedger(SocietyConfig cfg, String month) {
        String resolvedMonth = (month == null || month.isBlank())
                ? java.time.YearMonth.now().toString()
                : month;
        String buildingId = cfg.getBuildingId();

        BigDecimal expensesThisMonth = expenseRepo.sumForMonth(buildingId, resolvedMonth);
        BigDecimal expensesLifetime = expenseRepo.sumLifetime(buildingId);
        // Collections are 0 in the MVP — payment integration ships
        // these numbers in a later milestone.
        BigDecimal collectedThisMonth = BigDecimal.ZERO;
        BigDecimal collectedLifetime = BigDecimal.ZERO;
        BigDecimal outstandingThisMonth = BigDecimal.ZERO;
        BigDecimal balanceLifetime = collectedLifetime.subtract(expensesLifetime);

        List<MaintenanceExpense> rows = expenseRepo
                .findByBuildingIdAndExpenseMonthOrderByPaidOnDateDesc(buildingId, resolvedMonth);
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (MaintenanceExpense e : rows) {
            byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }

        return SocietyLedgerResponse.builder()
                .buildingId(buildingId)
                .societyDisplayName(cfg.getSocietyDisplayName())
                .month(resolvedMonth)
                .expensesThisMonth(expensesThisMonth)
                .collectedThisMonth(collectedThisMonth)
                .outstandingThisMonth(outstandingThisMonth)
                .balanceLifetime(balanceLifetime)
                .expensesLifetime(expensesLifetime)
                .collectedLifetime(collectedLifetime)
                .byCategory(byCategory)
                .expenses(rows.stream().map(mapper::toResponse).toList())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Building requireBuilding(String buildingId) {
        return buildingRepo.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Building not found: " + buildingId));
    }

    private SocietyConfig requireConfig(String buildingId) {
        return configRepo.findByBuildingId(buildingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not set up for this building yet."));
    }

    private void requireOwnerOrMaintainerOrAdmin(SocietyConfig cfg, Building b) {
        if (CallerSecurity.isAdmin()) return;
        String me = CallerSecurity.getCurrentAuthUserId().orElse(null);
        if (me == null) return; // Kafka/scheduler — system call
        if (me.equals(b.getOwnerId())) return;
        if (me.equals(cfg.getMaintainerUserId())) return;
        throw new ForbiddenException(
                "Only the building owner or the assigned maintainer can do that.");
    }

    private void requireOwnerOrMaintainerOrTenantOrAdmin(
            String buildingId, SocietyConfig cfg) {
        if (CallerSecurity.isAdmin()) return;
        String me = CallerSecurity.getCurrentAuthUserId().orElse(null);
        if (me == null) return; // System call
        Building b = buildingRepo.findById(buildingId).orElse(null);
        if (b != null && me.equals(b.getOwnerId())) return;
        if (me.equals(cfg.getMaintainerUserId())) return;
        // Any tenant living in a flat in this building can read.
        boolean isTenantHere = flatRepo.findByBuildingId(buildingId).stream()
                .anyMatch(f -> me.equals(f.getTenantId()));
        if (isTenantHere) return;
        throw new ForbiddenException(
                "Only residents, the owner, or the maintainer can view this ledger.");
    }

    /** 32-char URL-safe base64 — ~192 bits of entropy. */
    private static String generateToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
