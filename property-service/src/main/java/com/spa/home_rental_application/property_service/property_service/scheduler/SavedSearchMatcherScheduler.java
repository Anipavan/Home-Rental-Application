package com.spa.home_rental_application.property_service.property_service.scheduler;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.SavedSearch;
import com.spa.home_rental_application.property_service.property_service.client.NotificationClient;
import com.spa.home_rental_application.property_service.property_service.client.NotificationClient.SendBody;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.SavedSearchRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodic matcher for tenant saved searches.
 *
 * <p>Every {@code app.saved-search.match.interval-ms} milliseconds (default
 * 30 minutes), scans active {@link SavedSearch} rows and emits an email
 * alert for every newly-listed vacant flat that satisfies all set
 * predicates. The {@code lastMatchedAt} watermark on each saved search
 * advances to "now" after a successful run so a user never gets the
 * same alert twice.
 *
 * <p>Matching is conjunctive — every non-null predicate on the saved
 * search must hold for the flat. Predicates that span entities (e.g.
 * city, which lives on Building rather than Flat) hit a tiny in-memory
 * Building cache; the catalog is small enough that a per-tick reload
 * is cheap.
 *
 * <p>Notification dispatch goes through {@link NotificationClient},
 * which is Feign+Eureka — bypasses the gateway, no internal-auth
 * signing needed, and a notification-service outage is absorbed by
 * the Hystrix fallback so the matcher never crashes mid-batch.
 */
@Component
@Slf4j
public class SavedSearchMatcherScheduler {

    private final SavedSearchRepo savedSearchRepo;
    private final FlatRepo flatRepo;
    private final BuildingRepo buildingRepo;
    private final NotificationClient notifications;

    public SavedSearchMatcherScheduler(SavedSearchRepo savedSearchRepo,
                                       FlatRepo flatRepo,
                                       BuildingRepo buildingRepo,
                                       NotificationClient notifications) {
        this.savedSearchRepo = savedSearchRepo;
        this.flatRepo = flatRepo;
        this.buildingRepo = buildingRepo;
        this.notifications = notifications;
    }

    /**
     * Every 30 minutes by default. Tunable via
     * {@code app.saved-search.match.interval-ms}. Skip if no rows are
     * active so the job is essentially free on cold systems.
     */
    @Scheduled(fixedDelayString = "${app.saved-search.match.interval-ms:1800000}",
               initialDelayString = "${app.saved-search.match.initial-delay-ms:60000}")
    @Transactional
    public void matchAndNotify() {
        List<SavedSearch> active = savedSearchRepo.findByIsActiveTrue();
        if (active.isEmpty()) return;

        // Earliest watermark across all searches — used to bound the
        // single flat query so we don't pull the whole catalog.
        Instant earliestWatermark = active.stream()
                .map(SavedSearch::getLastMatchedAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(Instant.now().minusSeconds(86_400)); // fall back to 24h ago

        LocalDateTime since = LocalDateTime.ofInstant(earliestWatermark, ZoneId.systemDefault());
        List<Flat> candidates = flatRepo.findVacantCreatedAfter(since);
        if (candidates.isEmpty()) return;

        // Building cache — single bulk fetch by id keeps the city
        // predicate from triggering an N+1.
        Map<String, Building> buildingsById = loadBuildingsFor(candidates);

        Instant now = Instant.now();
        int alertsFired = 0;
        List<SavedSearch> dirty = new ArrayList<>();
        for (SavedSearch s : active) {
            Instant watermark = s.getLastMatchedAt() == null
                    ? earliestWatermark
                    : s.getLastMatchedAt();
            List<Flat> matches = new ArrayList<>();
            for (Flat f : candidates) {
                if (f.getCreatedAt() == null) continue;
                Instant flatListed = f.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
                if (!flatListed.isAfter(watermark)) continue;
                if (matches(s, f, buildingsById.get(f.getBuildingId()))) {
                    matches.add(f);
                }
            }

            // Audit H26: only advance the watermark when dispatch
            // actually succeeded. Previous code advanced unconditionally
            // — meaning a notification-service outage during fireAlert
            // would swallow the matches and the user would never get an
            // alert for those flats (the next run wouldn't reconsider
            // them because the watermark already moved past their
            // createdAt).
            boolean dispatchOk = true;
            if (!matches.isEmpty()) {
                dispatchOk = fireAlertReturningSuccess(s, matches, buildingsById);
                if (dispatchOk) alertsFired++;
            }

            if (matches.isEmpty() || dispatchOk) {
                // No matches → still bump the watermark so the next run
                // doesn't re-scan the same window. Successful dispatch
                // → same thing. Failed dispatch → keep the old watermark
                // so the next run gets another shot.
                s.setLastMatchedAt(now);
                dirty.add(s);
            } else {
                log.warn("SavedSearchMatcher: keeping watermark for searchId={} (dispatch failed; will retry)",
                        s.getId());
            }
        }
        if (!dirty.isEmpty()) {
            savedSearchRepo.saveAll(dirty);
        }
        log.info("SavedSearchMatcher: scanned {} active search(es), fired {} alert(s) over {} new flat(s)",
                active.size(), alertsFired, candidates.size());
    }

    private Map<String, Building> loadBuildingsFor(List<Flat> flats) {
        List<String> ids = flats.stream().map(Flat::getBuildingId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<String, Building> out = new HashMap<>();
        for (Building b : buildingRepo.findAllById(ids)) {
            out.put(b.getBuildingId(), b);
        }
        return out;
    }

    /**
     * Conjunctive predicate evaluation. A null field on the saved
     * search means "no constraint on that dimension", matching the
     * UI filter semantics.
     */
    private static boolean matches(SavedSearch s, Flat f, Building b) {
        if (s.getCity() != null && !s.getCity().isBlank()) {
            String flatCity = b == null ? null : b.getBuildingCity();
            if (flatCity == null || !flatCity.equalsIgnoreCase(s.getCity())) return false;
        }
        if (s.getBedrooms() != null && !s.getBedrooms().equals(f.getBedrooms())) return false;
        BigDecimal rent = f.getRentAmount();
        if (s.getMinRent() != null && (rent == null || rent.compareTo(s.getMinRent()) < 0)) return false;
        if (s.getMaxRent() != null && (rent == null || rent.compareTo(s.getMaxRent()) > 0)) return false;
        if (s.getMinAreaSqft() != null && (f.getAreaSqft() == null || f.getAreaSqft() < s.getMinAreaSqft())) return false;
        if (s.getFurnishingStatus() != null && !s.getFurnishingStatus().isBlank()) {
            if (f.getFurnishingStatus() == null
                    || !s.getFurnishingStatus().equalsIgnoreCase(f.getFurnishingStatus())) return false;
        }
        if (Boolean.TRUE.equals(s.getPetFriendly()) && !Boolean.TRUE.equals(f.getPetFriendly())) return false;
        return true;
    }

    /**
     * Compose one digest-style email per saved-search hit. We send a
     * single email even when N flats match so users don't get a
     * dozen pings from one matcher pass — that's how 99acres /
     * housing.com handle their batch alerts and it's the right
     * tradeoff for inbox hygiene.
     */
    /**
     * Wrapper used by the post-H26 main loop. Calls fireAlert, returns
     * true on a successful dispatch (no exception) and false otherwise
     * so the caller can decide whether to advance the watermark.
     */
    private boolean fireAlertReturningSuccess(SavedSearch s, List<Flat> matches,
                                              Map<String, Building> buildingsById) {
        try {
            fireAlert(s, matches, buildingsById);
            return true;
        } catch (Exception ex) {
            log.warn("SavedSearchMatcher: fireAlert threw for searchId={}: {}",
                    s.getId(), ex.getMessage());
            return false;
        }
    }

    private void fireAlert(SavedSearch s, List<Flat> matches, Map<String, Building> buildingsById) {
        String subject = "New match for your search: " + safeName(s) + " (" + matches.size() + " new)";
        StringBuilder body = new StringBuilder();
        body.append("Hi! ").append(matches.size())
            .append(matches.size() == 1 ? " new flat" : " new flats")
            .append(" just matched your saved search \"")
            .append(safeName(s)).append("\":\n\n");
        int shown = 0;
        for (Flat f : matches) {
            if (shown++ >= 5) {
                body.append("\n…and ").append(matches.size() - 5).append(" more.\n");
                break;
            }
            Building b = buildingsById.get(f.getBuildingId());
            body.append("• ");
            if (b != null && b.getBuildingName() != null) body.append(b.getBuildingName()).append(" — ");
            body.append(f.getBedrooms() == null ? "?" : f.getBedrooms()).append(" BHK");
            if (b != null && b.getBuildingCity() != null) body.append(" in ").append(b.getBuildingCity());
            if (f.getRentAmount() != null) body.append(" · ₹").append(f.getRentAmount()).append("/mo");
            body.append("\n");
        }
        body.append("\nOpen the app to view full details and book a visit.\n");
        body.append("\n— You can pause or delete this alert anytime from My Saved Searches.\n");

        try {
            notifications.sendEmail(SendBody.plainEmail(s.getUserId(), subject, body.toString()));
            log.info("SavedSearchMatcher: alert sent userId={} searchId={} matchCount={}",
                    s.getUserId(), s.getId(), matches.size());
        } catch (Exception ex) {
            // Rethrow so fireAlertReturningSuccess can refuse to
            // advance the watermark (H26). The notification-service
            // fallback already absorbs broker outages — exceptions
            // that bubble this far indicate the dispatch genuinely
            // didn't happen.
            log.warn("SavedSearchMatcher: alert dispatch failed userId={} searchId={}: {}",
                    s.getUserId(), s.getId(), ex.getMessage());
            throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
        }
    }

    private static String safeName(SavedSearch s) {
        return s.getName() == null || s.getName().isBlank() ? "My saved search" : s.getName();
    }
}
