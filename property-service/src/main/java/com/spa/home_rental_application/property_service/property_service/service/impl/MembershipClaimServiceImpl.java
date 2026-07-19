package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.DecideMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MembershipClaimResponse;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.FlatSocietyMembership;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.RequestedRole;
import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.Status;
import com.spa.home_rental_application.property_service.property_service.Entities.SocietyConfig;
import com.spa.home_rental_application.property_service.property_service.client.AuthClient;
import com.spa.home_rental_application.property_service.property_service.client.NotificationClient;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatSocietyMembershipRepository;
import com.spa.home_rental_application.property_service.property_service.repository.MembershipClaimRepository;
import com.spa.home_rental_application.property_service.property_service.repository.SocietyConfigRepository;
import com.spa.home_rental_application.property_service.property_service.security.CallerSecurity;
import com.spa.home_rental_application.property_service.property_service.security.ForbiddenException;
import com.spa.home_rental_application.property_service.property_service.service.MembershipClaimService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final FlatSocietyMembershipRepository membershipRepo;
    private final SocietyConfigRepository configRepo;
    private final UserClient userClient;
    private final AuthClient authClient;
    private final NotificationClient notificationClient;

    public MembershipClaimServiceImpl(MembershipClaimRepository claimRepo,
                                      BuildingRepo buildingRepo,
                                      FlatRepo flatRepo,
                                      FlatSocietyMembershipRepository membershipRepo,
                                      SocietyConfigRepository configRepo,
                                      UserClient userClient,
                                      AuthClient authClient,
                                      NotificationClient notificationClient) {
        this.claimRepo = claimRepo;
        this.buildingRepo = buildingRepo;
        this.flatRepo = flatRepo;
        this.membershipRepo = membershipRepo;
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

        if ((req.requestedRole() == RequestedRole.RESIDENT
                || req.requestedRole() == RequestedRole.FLAT_OWNER)
                && (req.claimedFlatNumber() == null || req.claimedFlatNumber().isBlank())) {
            throw new IllegalArgumentException(
                    "Flat number is required when applying as a resident or flat owner.");
        }

        // Maintainer claims now ALSO require a flat number — the
        // person managing the society's books has to actually live in
        // the building (be a tenant in one of its flats). Owner
        // self-claims are exempt: an owner already has total control
        // over the building they registered, so making them prove
        // residency would just be theatre.
        boolean isOwnerSelfClaim = req.requestedRole() == RequestedRole.MAINTAINER
                && userId.equals(building.getOwnerId());
        if (req.requestedRole() == RequestedRole.MAINTAINER
                && !isOwnerSelfClaim) {
            if (req.claimedFlatNumber() == null || req.claimedFlatNumber().isBlank()) {
                throw new IllegalArgumentException(
                        "Flat number is required — the maintainer must live in "
                                + "the building. Enter the flat you live in.");
            }
            // Look up the flat — refuse if it doesn't exist OR isn't
            // currently occupied. An "occupied" flat means someone is
            // a tenant there; the implicit assumption is that this
            // someone IS the applicant. The building owner verifies
            // identity at the approval step (they see the applicant's
            // name + email + the claimed flat number). The signup-side
            // gate stops obvious abuse: random users picking arbitrary
            // buildings, applicants picking empty flats they've never
            // lived in, etc.
            List<Flat> matches = flatRepo.findByBuildingIdAndFlatNumber(
                    building.getBuildingId(), req.claimedFlatNumber().trim());
            if (matches.isEmpty()) {
                throw new IllegalArgumentException(
                        "No flat numbered '" + req.claimedFlatNumber()
                                + "' exists in " + building.getBuildingName()
                                + ". Double-check the flat number with the owner.");
            }
            Flat targetFlat = matches.get(0);
            // Post-V15: a resident of the flat is EITHER a rental tenant
            // (flat.tenantId set) OR an active society member. Purely-
            // maintainee residents no longer touch flat.tenantId, so we
            // have to consult the membership table too, else a fresh
            // maintainer who joined the society first would fail this
            // gate on their own MAINTAINER application.
            boolean hasRentalTenant = Boolean.TRUE.equals(targetFlat.getIsOccupied())
                    && targetFlat.getTenantId() != null
                    && !targetFlat.getTenantId().isBlank();
            boolean hasSocietyMember = !membershipRepo
                    .findByFlatIdAndIsActiveTrue(targetFlat.getId())
                    .isEmpty();
            if (!hasRentalTenant && !hasSocietyMember) {
                throw new IllegalArgumentException(
                        "Flat " + req.claimedFlatNumber() + " in "
                                + building.getBuildingName() + " is currently vacant. "
                                + "Only a current resident can apply to maintain the society.");
            }
        }

        // RESIDENT (maintainee) — same vacancy guard. The owner has to
        // establish who lives in the flat FIRST (via the Assign-tenant
        // flow, which sets flat.tenantId), then residents of that flat
        // can self-register as society members. Without this, a random
        // user could pick any flat number in the building and — if the
        // maintainer waves them through — end up as a ghost resident of
        // a genuinely empty flat.
        //
        // We exclude the applicant themselves from the "other members"
        // count so a user re-submitting after an earlier withdrawal
        // isn't blocked by their own inactive row.
        if (req.requestedRole() == RequestedRole.RESIDENT) {
            List<Flat> matches = flatRepo.findByBuildingIdAndFlatNumber(
                    building.getBuildingId(), req.claimedFlatNumber().trim());
            if (matches.isEmpty()) {
                throw new IllegalArgumentException(
                        "No flat numbered '" + req.claimedFlatNumber()
                                + "' exists in " + building.getBuildingName()
                                + ". Double-check the flat number with the owner.");
            }
            Flat targetFlat = matches.get(0);
            boolean hasRentalTenant = targetFlat.getTenantId() != null
                    && !targetFlat.getTenantId().isBlank();
            long otherActiveMembers = membershipRepo
                    .findByFlatIdAndIsActiveTrue(targetFlat.getId()).stream()
                    .filter(m -> !userId.equals(m.getUserId()))
                    .count();
            if (!hasRentalTenant && otherActiveMembers == 0) {
                throw new IllegalArgumentException(
                        "Flat " + req.claimedFlatNumber() + " in "
                                + building.getBuildingName() + " is currently vacant. "
                                + "Ask the owner to assign a tenant to this flat first, "
                                + "then submit your request again.");
            }
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
        // they don't sit in PENDING waiting for themselves. Reuses the
        // `isOwnerSelfClaim` flag computed above (same condition);
        // kept as a named local for readability at the use sites below.
        boolean autoApprove = isOwnerSelfClaim;

        // Dual-approval flag: the claim needs BOTH owner + current
        // maintainer to approve before the swap fires when this is a
        // MAINTAINER claim targeting a building that already has an
        // active maintainer (and the claim isn't the owner self-
        // approving). Persisted at create-time so the requirement is
        // stable even if the current maintainer changes mid-flight.
        boolean requiresDual = !autoApprove
                && req.requestedRole() == RequestedRole.MAINTAINER
                && configRepo.findByBuildingId(building.getBuildingId())
                        .map(cfg -> cfg.getMaintainerUserId() != null
                                && !cfg.getMaintainerUserId().equals(building.getOwnerId()))
                        .orElse(false);

        MembershipClaim claim = MembershipClaim.builder()
                .buildingId(req.buildingId())
                .userId(userId)
                .requestedRole(req.requestedRole())
                .claimedFlatNumber(blankToNull(req.claimedFlatNumber()))
                .applicantNote(blankToNull(req.applicantNote()))
                .status(Status.PENDING)
                .requiresDualApproval(requiresDual)
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
            // Route the notification to whoever's actually going to
            // decide the claim (mirrors the listPending* + approve
            // authorization rules below):
            //   * MAINTAINER claims → owner (only owner can promote
            //     a maintainer).
            //   * RESIDENT / FLAT_OWNER claims where the building has
            //     a DISTINCT maintainer → that maintainer.
            //   * RESIDENT / FLAT_OWNER claims where no distinct
            //     maintainer exists (fresh building, or owner is also
            //     the maintainer) → owner as fallback.
            //   * Dual-approval MAINTAINER takeovers → ALSO ping the
            //     current maintainer so they know about the takeover.
            // Best-effort — notification failure must NEVER fail
            // claim creation (worst case the decider sees the chip on
            // next dashboard refresh).
            String distinctMaintainer = distinctMaintainerId(building);
            // Only RESIDENT (society-membership) claims delegate to the
            // maintainer. MAINTAINER + FLAT_OWNER always ping the owner
            // — see listPendingForOwner for the parallel filter.
            boolean routeToMaintainer = distinctMaintainer != null
                    && saved.getRequestedRole() == RequestedRole.RESIDENT;
            if (routeToMaintainer) {
                notifyCurrentMaintainerOfNewClaim(saved, building);
            } else {
                notifyOwnerOfNewClaim(saved, building);
            }
            if (requiresDual) {
                notifyCurrentMaintainerOfNewClaim(saved, building);
            }
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
     * Best-effort INAPP + EMAIL ping to the CURRENT maintainer that a
     * competing claim has been submitted. Fires only on dual-approval
     * claims (i.e. the building already has a maintainer). The
     * current maintainer's decision is part of the two-party gate
     * that protects against owner-account-compromise fraud.
     */
    private void notifyCurrentMaintainerOfNewClaim(MembershipClaim claim, Building building) {
        try {
            String currentMaintainerId = configRepo
                    .findByBuildingId(building.getBuildingId())
                    .map(SocietyConfig::getMaintainerUserId)
                    .orElse(null);
            if (currentMaintainerId == null) return;

            UserClient.UserSummary applicant = safeLookup(claim.getUserId());
            String who = applicant != null && applicant.fullName() != null
                    ? applicant.fullName()
                    : "Someone";
            String subject = "Someone wants to take over as maintainer of " + building.getBuildingName();
            String message = String.format(
                    "%s has applied to replace you as the maintainer of %s. "
                    + "Both you and the building owner need to approve before the change takes effect. "
                    + "Open your dashboard to review.",
                    who, building.getBuildingName());
            notificationClient.notifyUser(new NotificationClient.NotifyUserBody(
                    currentMaintainerId, subject, message));
            log.info("Notified current maintainer {} of dual-approval claim {} on building {}",
                    currentMaintainerId, claim.getId(), building.getBuildingId());
        } catch (Exception e) {
            log.warn("Notify current maintainer failed for claim {} on building {}: {}",
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

        // Ownership vs. society-membership split (updated 2026-07):
        //   * MAINTAINER claims → owner decides (only the owner can
        //     appoint a new maintainer).
        //   * FLAT_OWNER claims → owner decides (legal transfer of
        //     a flat's ownership is between the building owner and
        //     the incoming flat-owner; the society maintainer has no
        //     say in that transaction).
        //   * RESIDENT claims where a DISTINCT maintainer exists →
        //     that maintainer decides; hide from owner's list so the
        //     queue doesn't clutter with things they can't act on
        //     cleanly.
        //   * RESIDENT claims where no distinct maintainer is set
        //     (fresh building, or owner-as-maintainer) → still shown
        //     to owner as fallback so nothing gets stranded.
        Map<String, String> distinctMaintainerByBuilding = myBuildings.stream()
                .collect(Collectors.toMap(
                        Building::getBuildingId,
                        b -> {
                            String m = distinctMaintainerId(b);
                            return m == null ? "" : m;
                        }));

        List<MembershipClaim> forOwner = pending.stream()
                .filter(c -> {
                    // MAINTAINER + FLAT_OWNER always go to the owner.
                    if (c.getRequestedRole() == RequestedRole.MAINTAINER) return true;
                    if (c.getRequestedRole() == RequestedRole.FLAT_OWNER) return true;
                    // RESIDENT — owner only if no distinct maintainer.
                    String m = distinctMaintainerByBuilding.getOrDefault(c.getBuildingId(), "");
                    return m.isEmpty();
                })
                .toList();
        if (forOwner.isEmpty()) return List.of();

        // Batch-enrich: one user-service lookup per distinct claimant.
        Map<String, Building> bMap = myBuildings.stream()
                .collect(Collectors.toMap(Building::getBuildingId, b -> b));
        Map<String, UserClient.UserSummary> uMap = batchLookupUsers(
                forOwner.stream().map(MembershipClaim::getUserId).collect(Collectors.toSet()));

        return forOwner.stream()
                .map(c -> toResponse(c, bMap.get(c.getBuildingId()), uMap.get(c.getUserId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipClaimResponse> listPendingForCurrentMaintainer() {
        String userId = CallerSecurity.getCurrentAuthUserId()
                .orElseThrow(() -> new ForbiddenException(
                        "You must be logged in to view pending requests."));

        // Find every building this user currently maintains.
        List<SocietyConfig> mySocieties = configRepo.findByMaintainerUserId(userId);
        if (mySocieties.isEmpty()) return List.of();

        List<String> buildingIds = mySocieties.stream()
                .map(SocietyConfig::getBuildingId)
                .toList();

        // Two buckets:
        //   1. RESIDENT claims for buildings I maintain — society-
        //      membership decisions, my job. FLAT_OWNER claims are
        //      NOT included here: legal ownership transfers are the
        //      building owner's call, not the maintainer's.
        //   2. Dual-approval MAINTAINER takeover claims — the
        //      incumbent maintainer must weigh in.
        List<MembershipClaim> residencyClaims = claimRepo
                .findByBuildingIdInAndStatus(buildingIds, Status.PENDING).stream()
                .filter(c -> c.getRequestedRole() == RequestedRole.RESIDENT)
                .toList();
        List<MembershipClaim> dualApproval = claimRepo
                .findByBuildingIdInAndStatusAndRequiresDualApproval(
                        buildingIds, Status.PENDING, true);

        List<MembershipClaim> pending = Stream.concat(
                residencyClaims.stream(), dualApproval.stream())
                .distinct()
                .toList();
        if (pending.isEmpty()) return List.of();

        Map<String, Building> bMap = buildingRepo.findAllById(buildingIds).stream()
                .collect(Collectors.toMap(Building::getBuildingId, b -> b));
        Map<String, UserClient.UserSummary> uMap = batchLookupUsers(
                pending.stream().map(MembershipClaim::getUserId).collect(Collectors.toSet()));

        return pending.stream()
                .map(c -> toResponse(c, bMap.get(c.getBuildingId()), uMap.get(c.getUserId())))
                .toList();
    }

    /**
     * Returns the authUserId of the building's maintainer IFF a
     * DISTINCT maintainer is set (i.e. the SocietyConfig exists AND
     * its maintainerUserId is non-null AND != building.ownerId).
     * Otherwise null — meaning "no separate maintainer, owner is the
     * decider by default".
     */
    private String distinctMaintainerId(Building b) {
        return configRepo.findByBuildingId(b.getBuildingId())
                .map(SocietyConfig::getMaintainerUserId)
                .filter(mid -> mid != null && !mid.isBlank()
                        && !mid.equals(b.getOwnerId()))
                .orElse(null);
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

        if (claim.getStatus() != Status.PENDING) {
            throw new IllegalStateException(
                    "Claim is already " + claim.getStatus().name().toLowerCase() + ".");
        }

        String callerId = CallerSecurity.getCurrentAuthUserId().orElse(null);
        boolean callerIsOwner = callerId != null
                && callerId.equals(building.getOwnerId());
        // Dual-approval claims can be approved by EITHER the owner or
        // the current maintainer. Single-party claims (resident, or
        // maintainer-when-no-current-maintainer) require the owner.
        String currentMaintainerId = configRepo
                .findByBuildingId(building.getBuildingId())
                .map(SocietyConfig::getMaintainerUserId)
                .orElse(null);
        boolean callerIsCurrentMaintainer = callerId != null
                && callerId.equals(currentMaintainerId);

        if (Boolean.TRUE.equals(claim.getRequiresDualApproval())) {
            if (!callerIsOwner && !callerIsCurrentMaintainer && !CallerSecurity.isAdmin()) {
                throw new ForbiddenException(
                        "Only the building owner or the current maintainer can approve this claim.");
            }
            return decideDual(claim, building, req, callerId, callerIsOwner, true);
        }

        // Single-party flow.
        //
        // Phase 5 — maintainee (RESIDENT) claims can also be approved
        // by the building's current maintainer, not just the owner.
        // This is what lets Eshwar (maintainer of Sunshine Valley)
        // approve Siva's join request without Aarav (rental owner)
        // being in the loop. MAINTAINER + FLAT_OWNER claims are still
        // owner-only because those touch rental / ownership semantics.
        if (claim.getRequestedRole() == RequestedRole.RESIDENT
                && callerIsCurrentMaintainer) {
            // Maintainer of the society approves the maintainee.
            // No further check needed.
        } else {
            CallerSecurity.requireOwnerOrAdmin(building.getOwnerId());
        }

        switch (claim.getRequestedRole()) {
            case MAINTAINER -> applyMaintainerApproval(claim, building);
            case RESIDENT -> applyResidentApproval(claim, building);
            case FLAT_OWNER -> applyFlatOwnerApproval(claim, building);
        }

        claim.setStatus(Status.APPROVED);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setDecidedByUserId(callerId);
        claim.setOwnerDecidedAt(LocalDateTime.now());
        claim.setOwnerDecidedByUserId(callerId);
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

        if (claim.getStatus() != Status.PENDING) {
            throw new IllegalStateException(
                    "Claim is already " + claim.getStatus().name().toLowerCase() + ".");
        }

        String callerId = CallerSecurity.getCurrentAuthUserId().orElse(null);
        boolean callerIsOwner = callerId != null
                && callerId.equals(building.getOwnerId());
        String currentMaintainerId = configRepo
                .findByBuildingId(building.getBuildingId())
                .map(SocietyConfig::getMaintainerUserId)
                .orElse(null);
        boolean callerIsCurrentMaintainer = callerId != null
                && callerId.equals(currentMaintainerId);

        // Either party can reject a dual-approval claim — first to
        // reject kills it (no point requiring the second party to
        // also weigh in when one has already said no).
        if (Boolean.TRUE.equals(claim.getRequiresDualApproval())) {
            if (!callerIsOwner && !callerIsCurrentMaintainer && !CallerSecurity.isAdmin()) {
                throw new ForbiddenException(
                        "Only the building owner or the current maintainer can reject this claim.");
            }
        } else if (claim.getRequestedRole() == RequestedRole.RESIDENT
                && callerIsCurrentMaintainer) {
            // Phase 5 — maintainer can reject a maintainee claim.
        } else {
            CallerSecurity.requireOwnerOrAdmin(building.getOwnerId());
        }

        claim.setStatus(Status.REJECTED);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setDecidedByUserId(callerId);
        if (callerIsOwner) {
            claim.setOwnerDecidedAt(LocalDateTime.now());
            claim.setOwnerDecidedByUserId(callerId);
        } else if (callerIsCurrentMaintainer) {
            claim.setMaintainerDecidedAt(LocalDateTime.now());
            claim.setMaintainerDecidedByUserId(callerId);
        }
        claim.setDecisionNote(blankToNull(req == null ? null : req.decisionNote()));
        MembershipClaim saved = claimRepo.save(claim);
        notifyClaimantOfDecision(saved, building, false);
        return enrichResponse(saved);
    }

    /**
     * Dual-approval state machine for MAINTAINER reassign claims.
     * Records the calling party's decision; if the OTHER party hasn't
     * decided yet, the claim stays PENDING with the appropriate
     * *_decided_at column stamped. Once BOTH sides approve, the swap
     * fires and the claim transitions to APPROVED.
     */
    private MembershipClaimResponse decideDual(MembershipClaim claim,
                                                Building building,
                                                DecideMembershipClaimRequest req,
                                                String callerId,
                                                boolean isOwnerCaller,
                                                boolean isApproval) {
        LocalDateTime now = LocalDateTime.now();
        if (isOwnerCaller) {
            if (claim.getOwnerDecidedAt() != null) {
                throw new IllegalStateException(
                        "You've already decided on this claim. Wait for the current maintainer.");
            }
            claim.setOwnerDecidedAt(now);
            claim.setOwnerDecidedByUserId(callerId);
        } else {
            if (claim.getMaintainerDecidedAt() != null) {
                throw new IllegalStateException(
                        "You've already decided on this claim. Wait for the owner.");
            }
            claim.setMaintainerDecidedAt(now);
            claim.setMaintainerDecidedByUserId(callerId);
        }

        if (!isApproval) {
            claim.setStatus(Status.REJECTED);
            claim.setDecidedAt(now);
            claim.setDecidedByUserId(callerId);
            claim.setDecisionNote(blankToNull(req == null ? null : req.decisionNote()));
            MembershipClaim saved = claimRepo.save(claim);
            notifyClaimantOfDecision(saved, building, false);
            return enrichResponse(saved);
        }

        // Approval — but check the OTHER side's state. Stay PENDING
        // until they've also approved.
        if (claim.getOwnerDecidedAt() == null || claim.getMaintainerDecidedAt() == null) {
            log.info("Dual-approval partial for claim {}: owner={} maintainer={}",
                    claim.getId(),
                    claim.getOwnerDecidedAt() != null ? "approved" : "pending",
                    claim.getMaintainerDecidedAt() != null ? "approved" : "pending");
            MembershipClaim saved = claimRepo.save(claim);
            // Don't notify the claimant on a half-approval; they only
            // want to hear when the decision is final.
            return enrichResponse(saved);
        }

        // Both sides in — apply the swap.
        applyMaintainerApproval(claim, building);
        claim.setStatus(Status.APPROVED);
        claim.setDecidedAt(now);
        claim.setDecidedByUserId(callerId);
        claim.setDecisionNote(blankToNull(req == null ? null : req.decisionNote()));
        MembershipClaim saved = claimRepo.save(claim);
        notifyClaimantOfDecision(saved, building, true);
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
     * MAINTAINER claim approved → make sure {@code society_config} exists
     * for the building, then swap its {@code maintainer_user_id} to the
     * claimant and bump their auth role to MAINTAINER.
     *
     * <p>Previously refused when the building had no {@code SocietyConfig},
     * demanding the owner run "Set up society" as a prerequisite. That's
     * redundant friction — the maintainer BEING APPROVED is the person
     * who'll manage the society; forcing the owner to preconfigure
     * something the maintainer will re-configure anyway is unnecessary.
     * Auto-provision with safe defaults (per-flat=0, dueDay=5, display
     * name = building name); the maintainer refines these later from
     * their /maintainer dashboard.
     *
     * <p>Users already at OWNER/MAINTAINER/ADMIN role pass through the
     * auth call as a no-op.
     */
    private void applyMaintainerApproval(MembershipClaim claim, Building building) {
        LocalDateTime now = LocalDateTime.now();
        SocietyConfig cfg = configRepo.findByBuildingId(building.getBuildingId())
                .orElse(null);

        if (cfg == null) {
            byte[] tokenBytes = new byte[24];
            new SecureRandom().nextBytes(tokenBytes);
            String token = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tokenBytes);
            cfg = SocietyConfig.builder()
                    .buildingId(building.getBuildingId())
                    .monthlyDueDay(5)
                    .defaultPerFlatAmount(BigDecimal.ZERO)
                    .maintainerUserId(claim.getUserId())
                    .publicViewToken(token)
                    .societyDisplayName(building.getBuildingName())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            log.info("Auto-provisioned SocietyConfig for building {} on maintainer approval (new maintainer={})",
                    building.getBuildingId(), claim.getUserId());
        } else {
            String previous = cfg.getMaintainerUserId();
            cfg.setMaintainerUserId(claim.getUserId());
            cfg.setUpdatedAt(now);
            log.info("Maintainer swap on building {}: {} → {}",
                    building.getBuildingId(), previous, claim.getUserId());
        }
        configRepo.save(cfg);

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
     * RESIDENT claim approved → register the user as a society member
     * of the named flat and grant them the MAINTAINEE role.
     *
     * <p>V15: this used to also set {@code flat.tenantId} and
     * {@code flat.isOccupied=true}, which conflated the "someone lives
     * here for maintenance billing" concept with the "someone is
     * renting from the owner" concept. Owners saw self-registered
     * maintainees as rental tenants they'd never assigned. After V15
     * we route the maintenance-side relationship through
     * {@code flat_society_membership} and leave rental columns alone —
     * a flat can now have zero, one, or many society members
     * independently of whether it has a rental tenant.
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

        // Vacancy guard — mirrors the CREATE-time check but re-runs at
        // APPROVE time to close a race: the flat may have been vacated
        // (rental tenant vacated, other members deactivated) after
        // the applicant submitted. A truly empty flat shouldn't
        // acquire residents by way of self-registration — the owner
        // has to establish who lives there via the assign-tenant flow
        // first. Applicant themselves is excluded from the "other
        // members" count so a re-approval doesn't fail because of the
        // claimant's own pre-existing inactive row.
        boolean hasRentalTenant = flat.getTenantId() != null
                && !flat.getTenantId().isBlank();
        long otherActiveMembers = membershipRepo
                .findByFlatIdAndIsActiveTrue(flat.getId()).stream()
                .filter(m -> !claim.getUserId().equals(m.getUserId()))
                .count();
        if (!hasRentalTenant && otherActiveMembers == 0) {
            throw new IllegalStateException(
                    "Can't approve — flat " + flat.getFlatNumber() + " in "
                            + building.getBuildingName() + " is currently vacant. "
                            + "Ask the owner to assign a tenant to this flat first, "
                            + "then re-approve this request.");
        }

        // Idempotent upsert. A user who moved out (is_active=0) and
        // moves back in re-uses the same row instead of erroring on
        // the composite PK.
        String approverId = CallerSecurity.getCurrentAuthUserId().orElse(null);
        membershipRepo.findByFlatIdAndUserId(flat.getId(), claim.getUserId())
                .ifPresentOrElse(existing -> {
                    if (!Boolean.TRUE.equals(existing.getIsActive())) {
                        existing.setIsActive(true);
                        existing.setJoinedAt(LocalDateTime.now());
                        existing.setApprovedBy(approverId);
                        membershipRepo.save(existing);
                        log.info("Reactivated society membership: user {} → flat {} of building {}",
                                claim.getUserId(), flat.getFlatNumber(), building.getBuildingId());
                    } else {
                        log.info("Society membership already active — no-op: user {} → flat {} of building {}",
                                claim.getUserId(), flat.getFlatNumber(), building.getBuildingId());
                    }
                }, () -> {
                    membershipRepo.save(FlatSocietyMembership.builder()
                            .flatId(flat.getId())
                            .userId(claim.getUserId())
                            .joinedAt(LocalDateTime.now())
                            .approvedBy(approverId)
                            .isActive(true)
                            .build());
                    log.info("Created society membership: user {} → flat {} of building {}",
                            claim.getUserId(), flat.getFlatNumber(), building.getBuildingId());
                });

        // Grant MAINTAINEE role so the frontend routes them to the
        // slim society-focused dashboard on next login. authClient
        // preserves OWNER/MAINTAINER/ADMIN primary roles — MAINTAINEE
        // just becomes an additional facet in those cases.
        long authId;
        try {
            authId = Long.parseLong(claim.getUserId());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Internal: claim.userId '" + claim.getUserId()
                            + "' is not a numeric authUserId. The user was created "
                            + "outside the standard /auth/register flow.");
        }
        try {
            authClient.grantMaintaineeRole(authId);
        } catch (feign.FeignException fe) {
            log.error("auth-service grant-maintainee-role failed authUserId={} status={} body={}",
                    authId, fe.status(), fe.contentUTF8());
            throw new IllegalStateException(
                    "Couldn't update the user's role: " + fe.contentUTF8(), fe);
        }
    }

    /**
     * FLAT_OWNER claim approved (V8) → set flat.flatOwnerId to the
     * claimant. If the flat is currently vacant we also bind them as
     * the tenant (owner-occupier — the most common case for a buyer
     * moving in). If someone else is already living there as a renter,
     * we leave tenantId alone so we don't accidentally evict a tenant
     * the previous owner had on a valid lease.
     *
     * <p>Refuses if the flat already has a non-default owner — the
     * existing flat-owner has to be reassigned first. Going FROM the
     * building-owner's default IS allowed (this is exactly the "I
     * just sold flat 203" path the user described).
     */
    private void applyFlatOwnerApproval(MembershipClaim claim, Building building) {
        if (claim.getClaimedFlatNumber() == null || claim.getClaimedFlatNumber().isBlank()) {
            throw new IllegalArgumentException(
                    "Flat-owner claim is missing a flat number — reject and "
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

        String currentOwner = flat.getFlatOwnerId();
        boolean ownedByBuildingOwner = currentOwner == null
                || currentOwner.equals(building.getOwnerId());
        boolean alreadySameOwner = claim.getUserId().equals(currentOwner);

        if (!ownedByBuildingOwner && !alreadySameOwner) {
            throw new IllegalStateException(
                    "Flat " + flat.getFlatNumber()
                            + " is already owned by another user. The current flat-owner "
                            + "needs to reassign it first.");
        }

        flat.setFlatOwnerId(claim.getUserId());
        // Owner-occupier default: if no tenant is currently in the flat,
        // bind the new owner as the tenant too so dashboards show them
        // as the occupant. We DO NOT overwrite an existing tenantId —
        // the previous owner may have a legitimate renter who keeps
        // their tenancy through the ownership change.
        if (flat.getTenantId() == null || flat.getTenantId().isBlank()) {
            flat.setTenantId(claim.getUserId());
            flat.setIsOccupied(true);
        }
        flatRepo.save(flat);
        log.info("Flat-owner claim approved: user {} now owns flat {} of building {}",
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
