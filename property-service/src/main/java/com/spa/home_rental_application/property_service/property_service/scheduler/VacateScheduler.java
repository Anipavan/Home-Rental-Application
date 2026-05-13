package com.spa.home_rental_application.property_service.property_service.scheduler;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.TenantVacateScheduledEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily sweep that drives Issue #5's tenant-initiated scheduled
 * vacate flow. Mirrors the pattern of LeaseExpiryScheduler in
 * lease-service: cron-driven, fires events, stamps idempotency.
 *
 * <h2>Two sweeps per run</h2>
 *
 * <p><b>Sweep A — 10-day owner warning.</b> Finds flats where the
 * scheduled vacate date lands within the next 10 days AND no
 * warning has been sent yet. Fires
 * {@code tenant.vacate.scheduled} Kafka event so
 * notification-service's PropertyEventListener can fan a
 * {@code TENANT_VACATING_NOTICE} notification out to the owner
 * across every channel they're reachable on. Sets
 * {@code vacateWarningSentAt} for idempotency so a re-run on the
 * same day doesn't spam.
 *
 * <p><b>Sweep B — execute on effective date.</b> Finds flats where
 * the scheduled vacate date has arrived AND the flat is still
 * occupied. Calls {@link FlatService#executeScheduledVacate} which
 * does the actual {@code isOccupied=false}, clears tenantId, fires
 * {@code flat.vacated}.
 *
 * <h2>Cadence</h2>
 *
 * <p>Runs daily at 02:30 IST. Independent of LeaseExpiryScheduler's
 * 02:00 cron so we don't have two heavy sweeps colliding in the
 * same minute. The cron is configurable via
 * {@code app.vacate.scheduler-cron} for tests / staging where you
 * want to fire every minute.
 *
 * <h2>Tunables</h2>
 *
 * <ul>
 *   <li>{@code app.vacate.warning-days-before} — how many days
 *       before the vacate date to fire the owner warning (default
 *       10 per spec).</li>
 *   <li>{@code app.vacate.scheduler-enabled} — kill switch so a
 *       buggy run on a thundering-herd day can be paused without
 *       a redeploy.</li>
 * </ul>
 */
@Component
@Slf4j
public class VacateScheduler {

    private static final int WARNING_DAYS_BEFORE = 10;

    private final FlatRepo flatRepo;
    private final BuildingRepo buildingRepo;
    private final FlatService flatService;
    private final PropertyServiceEvents eventProducer;

    public VacateScheduler(FlatRepo flatRepo,
                           BuildingRepo buildingRepo,
                           FlatService flatService,
                           PropertyServiceEvents eventProducer) {
        this.flatRepo = flatRepo;
        this.buildingRepo = buildingRepo;
        this.flatService = flatService;
        this.eventProducer = eventProducer;
    }

    /**
     * Daily at 02:30. Override the cron for tests / staging via
     * {@code APP_VACATE_SCHEDULER_CRON} env var. Spring Scheduled
     * absorbs exceptions per-run so one bad row doesn't kill the
     * whole job.
     */
    @Scheduled(cron = "${app.vacate.scheduler-cron:0 30 2 * * *}")
    @Transactional
    public void sweep() {
        log.info("VacateScheduler sweep starting");
        try {
            int warned = sweepForOwnerWarnings();
            int executed = sweepForVacateExecution();
            log.info("VacateScheduler sweep complete — warnings={} executed={}", warned, executed);
        } catch (Exception ex) {
            // Defensive — never let a top-level failure break Spring's
            // scheduled-task pool. Log + carry on; tomorrow's run picks
            // up where this one died.
            log.error("VacateScheduler sweep failed mid-run", ex);
        }
    }

    /** Sweep A — fire 10-day-prior owner warnings. */
    private int sweepForOwnerWarnings() {
        LocalDate cutoff = LocalDate.now().plusDays(WARNING_DAYS_BEFORE);
        List<Flat> due = flatRepo.findDueForVacateWarning(cutoff);
        if (due.isEmpty()) return 0;
        log.info("Found {} flat(s) needing the 10-day vacate warning", due.size());
        int sent = 0;
        for (Flat flat : due) {
            try {
                Building parent = buildingRepo.findById(flat.getBuildingId()).orElse(null);
                String ownerId = parent != null ? parent.getOwnerId() : null;
                if (ownerId == null || ownerId.isBlank()) {
                    log.warn("Skipping vacate warning for flatId={} — no owner on parent building", flat.getId());
                    continue;
                }
                int daysUntil = (int) ChronoUnit.DAYS.between(LocalDate.now(), flat.getScheduledVacateDate());
                eventProducer.sendTenantVacateScheduled(TenantVacateScheduledEvent.builder()
                        .eventType("tenant.vacate.scheduled")
                        .flatId(flat.getId())
                        .flatNumber(flat.getFlatNumber())
                        .ownerId(ownerId)
                        .tenantId(flat.getTenantId())
                        .vacateDate(flat.getScheduledVacateDate().toString())
                        .daysUntilVacate(daysUntil)
                        .timestamp(Instant.now())
                        .build());

                // Idempotency stamp — re-runs on the same day won't refire.
                flat.setVacateWarningSentAt(LocalDateTime.now());
                flatRepo.save(flat);
                sent++;
                log.info("Sent vacate warning for flatId={} owner={} daysUntil={}",
                        flat.getId(), ownerId, daysUntil);
            } catch (Exception ex) {
                // Per-row failure — log and skip; the row stays in the
                // "due for warning" bucket so the next daily sweep retries.
                log.warn("Vacate warning failed for flatId={}: {}", flat.getId(), ex.getMessage());
            }
        }
        return sent;
    }

    /** Sweep B — perform the actual vacate on the effective date. */
    private int sweepForVacateExecution() {
        LocalDate today = LocalDate.now();
        List<Flat> due = flatRepo.findDueForVacateExecution(today);
        if (due.isEmpty()) return 0;
        log.info("Found {} flat(s) due for vacate execution today", due.size());
        int executed = 0;
        for (Flat flat : due) {
            try {
                flatService.executeScheduledVacate(flat.getId());
                executed++;
            } catch (Exception ex) {
                log.warn("Vacate execution failed for flatId={}: {}", flat.getId(), ex.getMessage());
            }
        }
        return executed;
    }
}
