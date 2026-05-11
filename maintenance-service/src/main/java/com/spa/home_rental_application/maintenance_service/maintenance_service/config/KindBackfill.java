package com.spa.home_rental_application.maintenance_service.maintenance_service.config;

import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-time backfill: stamp {@code kind = MAINTENANCE} on every legacy
 * row in {@code maintenance_requests} that pre-dates the Kind
 * discriminator.
 *
 * <p>The repository-level queries already absorb missing-kind rows for
 * MAINTENANCE filters ({@code findByKindIncludingLegacy} et al.), so
 * the user-visible bug is gone. This backfill is a follow-up cleanup
 * that lets the strict {@code findByKind} variants work uniformly and
 * keeps the data consistent enough to drop the "including legacy"
 * shims in a future commit.
 *
 * <p>Idempotent — re-running it does nothing because the predicate is
 * {@code {kind: {$exists: false}}}.
 */
@Component
@Slf4j
public class KindBackfill {

    private final MongoTemplate mongo;

    public KindBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        try {
            Query q = new Query(Criteria.where("kind").exists(false));
            Update u = new Update().set("kind", Kind.MAINTENANCE.name());
            long n = mongo.updateMulti(q, u, "maintenance_requests").getModifiedCount();
            if (n > 0) {
                log.info("KindBackfill: stamped {} legacy maintenance row(s) with kind=MAINTENANCE", n);
            } else {
                log.debug("KindBackfill: nothing to do (all rows already carry a kind)");
            }
        } catch (Exception ex) {
            // Don't fail startup over the backfill — the "including
            // legacy" queries still cover us. Just log it loudly.
            log.warn("KindBackfill failed (non-fatal): {}", ex.getMessage(), ex);
        }
    }
}
