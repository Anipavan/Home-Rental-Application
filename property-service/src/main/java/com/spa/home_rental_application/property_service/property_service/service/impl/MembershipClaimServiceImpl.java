package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.DecideMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MembershipClaimResponse;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.RequestedRole;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.Status;
import com.spa.home_rental_application.property_service.property_service.Entities.SocietyConfig;
import com.spa.home_rental_application.property_service.property_service.client.AuthClient;
import com.spa.home_rental_application.property_service.property_service.client.NotificationClient;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.MembershipClaimRepository;
import com.spa.home_rental_application.property_service.property_service.repository.SocietyConfigRepository;
import com.spa.home_rental_application.property_service.property_service.security.CallerSecurity;
import com.spa.home_rental_application.property_service.property_service.security.ForbiddenException;
import com.spa.home_rental_application.property_service.property_service.service.MembershipClaimService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Membership-claim implementation. Wires the existing services together:
 * <ul>
 *   <li>{@link BuildingRepo} — validates the claimed buildingId and
 *       resolves the building's owner for the approve/reject auth
 *       check.</li>
 *   <li>{@link FlatRepo} — finds the claimed flat by number on RESIDENT
 *       approval.</li>
 *   <li>{@link SocietyConfigRepository} — updated on MAINTAINER
 *       approval (assignment swap).</li>
 *   <li>{@link AuthClient#grantMaintainerRole} — bumps the user's role
 *       to MAINTAINER on MAINTAINER approval. Wrapped in a try/catch
 *       so a transient auth-service outage doesn't leave the building
 *       half-assigned.</li>
 *   <li>{@link UserClient#getUserByAuthId} — used at response-build
 *       time to enrich the owner widget with claimant name/email.</li>
 * </ul>
 *
 * <p>Auth model:
 * <ul>
 *   <li>{@code createClaim} — caller must be logged in (any role).
 *       userId is taken from the JWT, never from the body.</li>
 *   <li>{@code listPendingForOwner} / {@code approve} / {@code reject}
 *       — caller must own the building the claim targets. We resolve
 *       building.ownerId and pass it to {@link CallerSecurity}.</li>
 *   <li>{@code listMine} / {@code withdraw} — caller must be the
 *       claimant themselves (or admin).</li>
 * </ul>
 */
@Service
@Slf4j
public class MembershipClaimServiceImpl implements MembershipClaimService {

    private final MembershipClaimRepository claimRepo;
    private final BuildingRepo buildingRepo;
    private final FlatRepo flatRepo;
    private final SocietyConfigRepository configRepo;
    private final UserClient userClient;
    private final AuthClient authClient;
    private final NotificationClient notificationClient;

    public MembershipClaimServiceImpl(MembershipClaimRepository claimRepo,
                                      BuildingRepo buildingRepo,
                                      FlatRepo flatRepo,
                                      SocietyConfigRepository configRepo,
                                      UserClient userClient,
                                      AuthClient authClient,
                                      NotificationClient notificationClient) {
        this.claimRepo = claimRepo;
        this.buildingRepo = buildingRepo;
        this.flatRepo = flatRepo;
        this.configRepo = configRepo;
        this.userClient = userClient;
        this.authClient = authClient;
        this.notificationClient = notificationClient;
    }

    // ── Create ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public MembershipClaimResponse createClaim(CreateMembershipClaimRequest req) {
        String userId = CallerSecurity.getCurrentAuthUserId()
                .orElseThrow(() -> new ForbiddenException(
                        "You must be logged in to submit a membership claim."));

        Building building = buildingRepo.findById(req.buildingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Building not found — ask the owner to add it first."));

        if (req.requestedRole() == RequestedRole.RESIDENT
                && (req.claimedFlatNumber() == null || req.claimedFlatNumber().isBlank())) {
            throw new IllegalArgumentException(
                    "Flat number is required when applying as a resident.");
        }

        // Dedup — refuse a second PENDING claim from the same user on
        // the same building. They can withdraw the existing one and
        // resubmit if they got the role wrong.
        claimRepo.findFirstByBuildingIdAndUserIdAndStatus(
                req.buildingId(), userId, Status.PENDING).ifPresent(c -> {
            throw new IllegalStateException(
                    "You already have a pending claim for this building. "
                            + "Withdraw it before submitting a new one.");
        });

        // The owner self-claiming as maintainer of their own building
        // is a fast path: they already have the powers, no claim
        // needed. We still record it for audit, but auto-approve so
        // they don't sit in PENDING waiting for themselves.
        boolean autoApprove = req.requestedRole() == RequestedRole.MAINTAINER
                && userId.equals(building.getOwnerId());

        MembershipClaim claim = MembershipClaim.builder()
                .buildingId(req.buildingId())
                .userId(userId)
                .requestedRole(req.requestedRole())
                .claimedFlatNumber(blankToNull(req.claimedFlatNumber()))
                .applicantNote(blankToNull(req.applicantNote()))
                .status(Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        MembershipClaim saved = claimRepo.save(claim);

        if (autoApprove) {
            log.info("Auto-approving owner self-maintainer claim {} for building {}",
                    saved.getId(), req.buildingId());
            applyMaintainerApproval(saved, building);
            saved.setStatus(Status.APPROVED);
            saved.setDecidedAt(LocalDateTime.now());
            saved.setDecidedByUserId(userId);
            saved.setDecisionNote("Auto-approved (owner self-claim)");
            saved = claimRepo.save(saved);
        } else {
            // Ping the building owner so they don't have to discover
            // the request from the dashboard polling. Best-effort —
            // notification failure must NEVER fail claim creation
            // (worst case the owner just sees the chip on next
            // dashboard refresh).
            notifyOwnerOfNewClaim(saved, building);
        }

        return enrichResponse(saved);
    }

    /**
     * Fire-and-forget INAPP + EMAIL ping to the building owner that a
     * new membership claim has landed. Looks up the applicant's name
     * for a friendlier subject line, falls back to "Someone" if user-
     * service is unreachable. Wraps the notification-service call in
     * try/catch so a notifier outage doesn't propagate.
     */
    private void notifyOwnerOfNewClaim(MembershipClaim claim, Building building) {
        try {
            UserClient.UserSummary applicant = safeLookup(claim.getUserId());
            String who = applicant != null && applicant.fullName() != null
                    ? applicant.fullName()
                    : "Someone";
            String roleLabel = claim.getRequestedRole() == RequestedRole.MAINTAINER
                    ? "as the society maintainer"
                    : ("as a resident of flat " + claim.getClaimedFlatNumber());
            String subject = "New society request for " + building.getBuildingName();
            String message = String.format(
                    "%s wants to join %s %s. Open your dashboard to approve or reject the request.",
                    who, building.getBuildingName(), roleLabel);
            notificationClient.notifyUser(new NotificationClient.NotifyUserBody(
                    building.getOwnerId(), subject, message));
            log.info("Notified owner {} of new claim {} on building {}",
                    building.getOwnerId(), claim.getId(), building.getBuildingId());
        } catch (Exception e) {
            // Swallow — the owner will still see the claim on their
            // dashboard widget. The notification is a nice-to-have.
            log.warn("Notify owner failed for claim {} on building {}: {}",
                    claim.getId(), building.getBuildingId(), e.getMessage());
        }
    }

    /**
     * Best-effort notification to the claimant when the owner makes a
     * decision. Mirrors {@link #notifyOwnerOfNewClaim} for the
     * outbound direction (we already let the polling page show it,
     * but a bell-entry + email is the right UX).
     */
    private void notifyClaimantOfDecision(MembershipClaim claim, Building building, boolean approved) {
        try {
            String subject = approved
                    ? "Your request for " + building.getBuildingName() + " was approved"
                    : "Your request for " + building.getBuildingName() + " was rejected";
            String message = approved
                    ? (claim.getRequestedRole() == RequestedRole.MAINTAINER
                        ? "You can now manage the society. Sign out and back in to pick up your maintainer dashboard."
                        : "You're attached to your flat. Open the app to see the society books.")
                    : ("Note from the owner: "
                        + (claim.getDecisionNote() == null || claim.getDecisionNote().isBlank()
                            ? "no reason given."
                            : claim.getDecisionNote()));
            notificationClient.notifyUser(new NotificationClient.NotifyUserBody(
                    claim.getUserId(), subject, message));
        } catch (Exception e) {
            log.warn("Notify claimant failed for claim {} on building {}: {}",
                    claim.getId(), building.getBuildingId(), e.getMessage());
        }
    }

    // ── Read ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MembershipClaimResponse> listPendingForOwner() {
        String ownerId = CallerSecurity.getCurrentAuthUserId()
                .orElseThrow(() -> new ForbiddenException(
                        "You must be logged in to view pending requests."));

        // Find every building this user owns; if zero, return [] fast.
        List<Building> myBuildings = buildingRepo.findAll().stream()
                .filter(b -> ownerId.equals(b.getOwnerId()))
                .toList();
        if (myBuildings.isEmpty()) return List.of();

        List<String> buildingIds = myBuildings.stream().map(Building::getBuildingId).toList();
        List<MembershipClaim> pending =
                claimRepo.findByBuildingIdInAndStatus(buildingIds, Status.PENDING);
        if (pending.isEmpty()) return List.of();

        // Batch-enrich: one user-service lookup per distinct claimant
        // (not per-claim), and a single in-memory map for building
        // display fields. Keeps the widget snappy for owners with
        // many small buildings.
        Map<String, Building> bMap = myBuildings.stream()
                .collect(Collectors.toMap(Building::getBuildingId, b -> b));
        Map<String, UserClient.UserSummary> uMap = batchLookupUsers(
                pending.stream().map(MembershipClaim::getUserId).collect(Collectors.toSet()));

        return pending.stream()
                .map(c -> toResponse(c, bMap.get(c.getBuildingId()), uMap.get(c.getUserId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipClaimResponse> listMine() {
        String userId = CallerSecurity.getCurrentAuthUserId()
                .orElseThrow(() -> new ForbiddenException(
                        "You must be logged in to view your claims."));
        List<MembershipClaim> mine = claimRepo.findByUserId(userId);
        if (mine.isEmpty()) return List.of();

        Set<String> bIds = mine.stream()
                .map(MembershipClaim::getBuildingId).collect(Collectors.toSet());
        Map<String, Building> bMap = buildingRepo.findAllById(bIds).stream()
                .collect(Collectors.toMap(Building::getBuildingId, b -> b));

        // userId is always the caller — single lookup.
        UserClient.UserSummary me = safeLookup(userId);
        Map<String, UserClient.UserSummary> uMap = new HashMap<>();
        uMap.put(userId, me);

        return mine.stream()
                .map(c -> toResponse(c, bMap.get(c.getBuildingId()), me))
                .toList();
    }

    // ── Decide ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public MembershipClaimResponse approveClaim(String claimId, DecideMembershipClaimRequest req) {
        MembershipClaim claim = requireClaim(claimId);
        Building building = requireBuilding(claim.getBuildingId());
        CallerSecurity.requireOwnerOrAdmin(building.getOwnerId());

        if (claim.getStatus() != Status.PENDING) {
            throw new IllegalStateException(
                    "Claim is already " + claim.getStatus().name().toLowerCase() + ".");
        }

        if (claim.getRequestedRole() == RequestedRole.MAINTAINER) {
            applyMaintainerApproval(claim, building);
        } else {
            applyResidentApproval(claim, building);
        }

        claim.setStatus(Status.APPROVED);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setDecidedByUserId(CallerSecurity.getCurrentAuthUserId().orElse(null));
        claim.setDecisionNote(blankToNull(req == null ? null : req.decisionNote()));
        MembershipClaim saved = claimRepo.save(claim);
        notifyClaimantOfDecision(saved, building, true);
        return enrichResponse(saved);
    }

    @Override
    @Transactional
    public MembershipClaimResponse rejectClaim(String claimId, DecideMembershipClaimRequest req) {
        MembershipClaim claim = requireClaim(claimId);
        Building building = requireBuilding(claim.getBuildingId());
        CallerSecurity.requireOwnerOrAdmin(building.getOwnerId());

        if (claim.getStatus() != Status.PENDING) {
            throw new IllegalStateException(
                    "Claim is already " + claim.getStatus().name().toLowerCase() + ".");
        }

        claim.setStatus(Status.REJECTED);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setDecidedByUserId(CallerSecurity.getCurrentAuthUserId().orElse(null));
        claim.setDecisionNote(blankToNull(req == null ? null : req.decisionNote()));
        MembershipClaim saved = claimRepo.save(claim);
        notifyClaimantOfDecision(saved, building, false);
        return enrichResponse(saved);
    }

    @Override
    @Transactional
    public MembershipClaimResponse withdrawClaim(String claimId) {
        MembershipClaim claim = requireClaim(claimId);
        CallerSecurity.requireSelfOrAdmin(claim.getUserId());

        if (claim.getStatus() != Status.PENDING) {
            // No-op: the owner already decided; nothing to withdraw.
            return enrichResponse(claim);
        }

        claim.setStatus(Status.WITHDRAWN);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setDecidedByUserId(claim.getUserId());
        return enrichResponse(claimRepo.save(claim));
    }

    // ── Side-effects on approval ───────────────────────────────────

    /**
     * MAINTAINER claim approved → swap {@code society_config.maintainer_
     * user_id} and bump the user's role to MAINTAINER. If the building
     * has no society config yet, refuse — the owner needs to set up
     * the society first (default per-flat amount, etc.). Users
     * already at OWNER/MAINTAINER/ADMIN role pass through the auth
     * call as a no-op.
     */
    private void applyMaintainerApproval(MembershipClaim claim, Building building) {
        SocietyConfig cfg = configRepo.findByBuildingId(building.getBuildingId())
                .orElseThrow(() -> new IllegalStateException(
                        "Approve refused — set up the society for this "
                                + "building first (Society → Set up)."));

        String previous = cfg.getMaintainerUserId();
        cfg.setMaintainerUserId(claim.getUserId());
        cfg.setUpdatedAt(LocalDateTime.now());
        configRepo.save(cfg);
        log.info("Maintainer swap on building {}: {} → {}", building.getBuildingId(), previous, claim.getUserId());

        // Auth-service role bump. Wrapped so a transient auth-service
        // outage surfaces as a readable error rather than leaving the
        // society half-assigned + user still TENANT.
        Long authId;
        try {
            authId = Long.valueOf(claim.getUserId());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "Internal: claim.userId '" + claim.getUserId()
                            + "' is not a numeric authUserId. The user was created "
                            + "outside the standard /auth/register flow.");
        }
        try {
            authClient.grantMaintainerRole(authId);
        } catch (feign.FeignException fe) {
            log.error("auth-service grant-maintainer-role failed authUserId={} status={} body={}",
                    authId, fe.status(), fe.contentUTF8());
            throw new IllegalStateException(
                    "Couldn't update the user's role: " + fe.contentUTF8(), fe);
        }
    }

    /**
     * RESIDENT claim approved → bind the user as tenant of the named
     * flat. Refuses if the flat is currently occupied — the owner has
     * to vacate it first via the existing flow.
     */
    private void applyResidentApproval(MembershipClaim claim, Building building) {
        if (claim.getClaimedFlatNumber() == null || claim.getClaimedFlatNumber().isBlank()) {
            throw new IllegalArgumentException(
                    "Resident claim is missing a flat number — reject and "
                            + "ask the applicant to resubmit with a flat number.");
        }
        List<Flat> matches = flatRepo.findByBuildingIdAndFlatNumber(
                building.getBuildingId(), claim.getClaimedFlatNumber());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No flat numbered '" + claim.getClaimedFlatNumber()
                            + "' exists in this building.");
        }
        Flat flat = matches.get(0);
        if (Boolean.TRUE.equals(flat.getIsOccupied())
                && flat.getTenantId() != null
                && !flat.getTenantId().equals(claim.getUserId())) {
            throw new IllegalStateException(
                    "Flat " + flat.getFlatNumber()
                            + " is already occupied. Vacate the existing tenant first.");
        }
        flat.setTenantId(claim.getUserId());
        flat.setIsOccupied(true);
        flatRepo.save(flat);
        log.info("Resident claim approved: user {} bound to flat {} of building {}",
                claim.getUserId(), flat.getFlatNumber(), building.getBuildingId());
    }

    // ── Helpers ────────────────────────────────────────────────────

    private MembershipClaim requireClaim(String id) {
        return claimRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Membership claim not found: " + id));
    }

    private Building requireBuilding(String id) {
        return buildingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Building not found: " + id));
    }

    private MembershipClaimResponse enrichResponse(MembershipClaim c) {
        Building b = buildingRepo.findById(c.getBuildingId()).orElse(null);
        UserClient.UserSummary u = safeLookup(c.getUserId());
        return toResponse(c, b, u);
    }

    private MembershipClaimResponse toResponse(
            MembershipClaim c, Building b, UserClient.UserSummary u) {
        return MembershipClaimResponse.of(
                c,
                b == null ? null : b.getBuildingName(),
                b == null ? null : b.getBuildingCity(),
                u == null ? null : u.fullName(),
                u == null ? null : u.email()
        );
    }

    /** Best-effort batch user-summary lookup. Failures surface as
     *  null entries — the response still renders but with blank
     *  claimant fields rather than a 500. */
    private Map<String, UserClient.UserSummary> batchLookupUsers(Set<String> userIds) {
        Map<String, UserClient.UserSummary> out = new HashMap<>(userIds.size());
        for (String id : userIds) {
            out.put(id, safeLookup(id));
        }
        return out;
    }

    private UserClient.UserSummary safeLookup(String userId) {
        try {
            return userClient.getUserByAuthId(userId);
        } catch (Exception e) {
            log.debug("user lookup failed for {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
