package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AddExpenseRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.PromoteTenantToMaintainerRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.SetupSocietyRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.UpsertFlatCollectionRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.EligibleMaintainerResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatMaintenanceRowResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MaintenanceExpenseResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.PromoteTenantResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyConfigResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyLedgerResponse;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceCollection;
import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceExpense;
import com.spa.home_rental_application.property_service.property_service.Entities.SocietyConfig;
import com.spa.home_rental_application.property_service.property_service.Mapper.SocietyMapper;
import com.spa.home_rental_application.property_service.property_service.client.AuthClient;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.enums.CollectionStatus;
import com.spa.home_rental_application.property_service.property_service.enums.ExpenseCategory;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.MaintenanceCollectionRepository;
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
    private final MaintenanceCollectionRepository collectionRepo;
    private final BuildingRepo buildingRepo;
    private final FlatRepo flatRepo;
    private final SocietyMapper mapper;
    private final UserClient userClient;
    private final AuthClient authClient;

    public SocietyServiceImpl(SocietyConfigRepository configRepo,
                              MaintenanceExpenseRepository expenseRepo,
                              MaintenanceCollectionRepository collectionRepo,
                              BuildingRepo buildingRepo,
                              FlatRepo flatRepo,
                              SocietyMapper mapper,
                              UserClient userClient,
                              AuthClient authClient) {
        this.configRepo = configRepo;
        this.expenseRepo = expenseRepo;
        this.collectionRepo = collectionRepo;
        this.buildingRepo = buildingRepo;
        this.flatRepo = flatRepo;
        this.mapper = mapper;
        this.userClient = userClient;
        this.authClient = authClient;
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
        // Collections now come from maintenance_collection rows the
        // maintainer marks PAID via the per-flat dashboard. Until a
        // single PAID row exists, every number stays at zero — same
        // visual result as the previous MVP placeholder, but driven by
        // real data instead of hard-coded zeros.
        BigDecimal collectedThisMonth =
                collectionRepo.sumCollectedForMonth(buildingId, resolvedMonth);
        BigDecimal collectedLifetime =
                collectionRepo.sumCollectedLifetime(buildingId);
        BigDecimal outstandingThisMonth =
                collectionRepo.sumOutstandingForMonth(buildingId, resolvedMonth);
        // "This year" derived from the resolved month's YYYY prefix —
        // covers the (rare) case where the user is browsing back to a
        // prior calendar year and wants the year-total for THAT year,
        // not the current calendar year.
        String yearPrefix = resolvedMonth.substring(0, 4) + "-%";
        BigDecimal collectedThisYear =
                collectionRepo.sumCollectedForYear(buildingId, yearPrefix);
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
                .collectedThisYear(collectedThisYear)
                .outstandingThisMonth(outstandingThisMonth)
                .balanceLifetime(balanceLifetime)
                .expensesLifetime(expensesLifetime)
                .collectedLifetime(collectedLifetime)
                .byCategory(byCategory)
                .expenses(rows.stream().map(mapper::toResponse).toList())
                .build();
    }

    // ── Maintainer assignment (owner-driven) ──────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<EligibleMaintainerResponse> listEligibleMaintainers(String buildingId) {
        Building b = requireBuilding(buildingId);
        // Only the owner / admin can see the tenant roster for the
        // purpose of picking a maintainer. Tenants and other roles do
        // not get this list — even the existing maintainer can't see
        // it (they can't reassign themselves anyway).
        CallerSecurity.requireOwnerOrAdmin(b.getOwnerId());

        List<Flat> flats = flatRepo.findByBuildingId(buildingId);
        return flats.stream()
                // Only consider currently-occupied flats. Vacant flats
                // have no tenantId, so they trivially fall out of the
                // filter.
                .filter(f -> f.getTenantId() != null && !f.getTenantId().isBlank())
                .map(f -> {
                    UserClient.UserSummary u;
                    try {
                        u = userClient.getUserByAuthId(f.getTenantId());
                    } catch (Exception ex) {
                        // user-service unavailable / Feign blip — render
                        // a "Flat 101 — Tenant abc12345…" entry so the
                        // owner still has SOMETHING to pick. Worst case
                        // they don't recognise the placeholder and ask
                        // the tenant directly.
                        log.warn("eligible-maintainers user-service blip authUserId={}", f.getTenantId(), ex);
                        u = UserClient.UserSummary.empty();
                    }
                    String fullName = u == null ? null : u.fullName();
                    String shownName = (fullName == null || fullName.isBlank())
                            ? ("Tenant " + safeShortId(f.getTenantId()))
                            : fullName;
                    String displayName = "Flat " + f.getFlatNumber() + " — " + shownName;
                    return EligibleMaintainerResponse.builder()
                            .tenantUserId(f.getTenantId())
                            .flatId(f.getId())
                            .flatNumber(f.getFlatNumber())
                            .tenantName(shownName)
                            .displayName(displayName)
                            .email(u == null ? null : u.email())
                            .phone(u == null ? null : u.phone())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public PromoteTenantResponse promoteTenantToMaintainer(
            String buildingId, PromoteTenantToMaintainerRequest req) {
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        // Owner / admin only — a maintainer cannot replace themselves
        // by promoting someone else.
        CallerSecurity.requireOwnerOrAdmin(b.getOwnerId());

        // Verify the target is currently a tenant of one of THIS
        // building's flats. Defends against the owner POSTing a random
        // authUserId (or a tenant of a different building they don't
        // own) by manipulating the API.
        boolean ok = flatRepo.findByBuildingId(buildingId).stream()
                .anyMatch(f -> req.tenantUserId().equals(f.getTenantId()));
        if (!ok) {
            throw new IllegalArgumentException(
                    "User is not a tenant of any flat in this building — "
                            + "refresh the list and pick a current resident.");
        }

        // 1. auth-service: role flip + password reset + token revoke.
        //    Feign exceptions bubble; controller's global handler maps
        //    them to a meaningful HTTP error.
        Long authId;
        try {
            authId = Long.valueOf(req.tenantUserId());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "Internal: tenantUserId is not a numeric authUserId — "
                            + "auth-service stores Long ids.");
        }
        AuthClient.AuthUserSummary updated = authClient.promoteToMaintainer(
                authId, new AuthClient.PromoteBody(req.temporaryPassword()));

        // 2. society config: link the new maintainer.
        cfg.setMaintainerUserId(req.tenantUserId().trim());
        cfg.setUpdatedAt(LocalDateTime.now());
        configRepo.save(cfg);

        log.info("Tenant promoted to maintainer buildingId={} tenantUserId={} byOwner={}",
                buildingId, req.tenantUserId(),
                CallerSecurity.getCurrentAuthUserId().orElse("(no-caller)"));

        String msg = "Share these credentials with " + updated.userName()
                + " via WhatsApp / in-person. Ask them to change the password "
                + "on first login.";
        return PromoteTenantResponse.builder()
                .tenantUserId(req.tenantUserId())
                .userName(updated.userName())
                .temporaryPassword(req.temporaryPassword())
                .message(msg)
                .build();
    }

    // ── Per-flat collections (maintainer dashboard) ───────────────

    @Override
    @Transactional(readOnly = true)
    public List<FlatMaintenanceRowResponse> listFlatsForMonth(String buildingId, String month) {
        SocietyConfig cfg = requireConfig(buildingId);
        // Owner / maintainer / admin only — tenants don't see other
        // tenants' bills.
        requireOwnerOrMaintainerOrAdmin(cfg, requireBuilding(buildingId));

        String resolvedMonth = (month == null || month.isBlank())
                ? java.time.YearMonth.now().toString()
                : month;

        BigDecimal defaultAmount = cfg.getDefaultPerFlatAmount();
        List<Flat> flats = flatRepo.findByBuildingId(buildingId);
        return flats.stream().map(f -> {
            // Resolve the tenant's display name. Stays best-effort —
            // an empty UserSummary falls through to "—" rather than
            // blocking the whole dashboard on a user-service blip.
            String tenantName = "(vacant)";
            if (f.getTenantId() != null && !f.getTenantId().isBlank()) {
                try {
                    UserClient.UserSummary u = userClient.getUserByAuthId(f.getTenantId());
                    String fullName = u == null ? null : u.fullName();
                    tenantName = (fullName == null || fullName.isBlank())
                            ? ("Tenant " + safeShortId(f.getTenantId()))
                            : fullName;
                } catch (Exception ex) {
                    log.warn("flats-for-month user-service blip authUserId={}", f.getTenantId(), ex);
                    tenantName = "Tenant " + safeShortId(f.getTenantId());
                }
            }

            // Look up the (flat, month) collection row. Missing row =
            // "no entry yet for this month" — render the building
            // default as the displayed amount and flag status NEW_FLAT.
            Optional<MaintenanceCollection> row = collectionRepo
                    .findByFlatIdAndForMonth(f.getId(), resolvedMonth);
            BigDecimal monthAmount = row.map(MaintenanceCollection::getAmountDue)
                    .orElse(defaultAmount);
            String status = row.map(r -> r.getStatus().name()).orElse("NEW_FLAT");

            return FlatMaintenanceRowResponse.builder()
                    .flatId(f.getId())
                    .flatNumber(f.getFlatNumber())
                    .tenantUserId(f.getTenantId())
                    .tenantName(tenantName)
                    .monthAmount(monthAmount)
                    .status(status)
                    .defaultAmount(defaultAmount)
                    .forMonth(resolvedMonth)
                    .notes(row.map(MaintenanceCollection::getNotes).orElse(null))
                    .paidOn(row.map(MaintenanceCollection::getPaidOn).orElse(null))
                    .paidVia(row.map(MaintenanceCollection::getPaidVia).orElse(null))
                    .amountPaid(row.map(MaintenanceCollection::getAmountPaid).orElse(null))
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public FlatMaintenanceRowResponse upsertFlatCollection(
            String buildingId, String flatId, UpsertFlatCollectionRequest req) {
        SocietyConfig cfg = requireConfig(buildingId);
        Building b = requireBuilding(buildingId);
        // Owner + maintainer + admin can write. Tenants cannot — they
        // would otherwise be able to mark their own bills PAID without
        // money changing hands.
        requireOwnerOrMaintainerOrAdmin(cfg, b);

        // Verify the flat actually belongs to this building. Cross-
        // building writes via path-param tampering get a 400.
        Flat flat = flatRepo.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Flat not found: " + flatId));
        if (!buildingId.equals(flat.getBuildingId())) {
            throw new IllegalArgumentException(
                    "Flat " + flatId + " does not belong to building " + buildingId);
        }

        String me = CallerSecurity.getCurrentAuthUserId().orElse(cfg.getMaintainerUserId());
        LocalDateTime now = LocalDateTime.now();

        MaintenanceCollection row = collectionRepo
                .findByFlatIdAndForMonth(flatId, req.forMonth())
                .orElseGet(() -> MaintenanceCollection.builder()
                        .buildingId(buildingId)
                        .flatId(flatId)
                        .forMonth(req.forMonth())
                        .amountDue(req.amountDue())
                        .status(CollectionStatus.DUE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        // Update fields. amountDue is required; status defaults to DUE
        // on create and keeps prior value on update if not specified.
        row.setAmountDue(req.amountDue());
        if (req.status() != null) {
            row.setStatus(req.status());
        }
        row.setNotes(req.notes());
        row.setMarkedByUserId(me);
        if (req.paidOn() != null) row.setPaidOn(req.paidOn());
        if (req.amountPaid() != null) row.setAmountPaid(req.amountPaid());
        if (req.paidVia() != null && !req.paidVia().isBlank()) {
            row.setPaidVia(req.paidVia().trim());
        }
        row.setUpdatedAt(now);

        // Ensure required-on-insert fields when JpaRepository.save flips
        // into INSERT path on the orElseGet branch.
        if (row.getCreatedAt() == null) row.setCreatedAt(now);
        if (row.getBuildingId() == null) row.setBuildingId(buildingId);
        if (row.getFlatId() == null) row.setFlatId(flatId);
        if (row.getForMonth() == null) row.setForMonth(req.forMonth());

        MaintenanceCollection saved = collectionRepo.save(row);
        log.info("Flat collection upserted buildingId={} flatId={} month={} status={}",
                buildingId, flatId, req.forMonth(), saved.getStatus());

        // Re-resolve the tenant display name for the response — keeps
        // the maintainer dashboard's optimistic-update path consistent
        // with the listFlatsForMonth shape.
        String tenantName = "(vacant)";
        if (flat.getTenantId() != null && !flat.getTenantId().isBlank()) {
            try {
                UserClient.UserSummary u = userClient.getUserByAuthId(flat.getTenantId());
                String fullName = u == null ? null : u.fullName();
                tenantName = (fullName == null || fullName.isBlank())
                        ? ("Tenant " + safeShortId(flat.getTenantId()))
                        : fullName;
            } catch (Exception ignored) {
                tenantName = "Tenant " + safeShortId(flat.getTenantId());
            }
        }

        return FlatMaintenanceRowResponse.builder()
                .flatId(flat.getId())
                .flatNumber(flat.getFlatNumber())
                .tenantUserId(flat.getTenantId())
                .tenantName(tenantName)
                .monthAmount(saved.getAmountDue())
                .status(saved.getStatus().name())
                .defaultAmount(cfg.getDefaultPerFlatAmount())
                .forMonth(saved.getForMonth())
                .notes(saved.getNotes())
                .paidOn(saved.getPaidOn())
                .paidVia(saved.getPaidVia())
                .amountPaid(saved.getAmountPaid())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Short-id helper for placeholder tenant names — never NPEs on null. */
    private static String safeShortId(String id) {
        if (id == null || id.length() < 8) return id == null ? "?" : id;
        return id.substring(0, 8) + "…";
    }

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
