package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.repositry.BankAccountRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * P1-13: scheduled hard-deletion of soft-deleted user rows past the
 * legal retention window. Implements the GDPR / India DPDP "right to
 * be forgotten" — soft-delete already happens via the regular delete
 * endpoint (sets {@code User.isDeleted=true} + {@code deletedAt=now}),
 * but the row must eventually be purged from the database.
 *
 * <p>Default retention window: 30 days post-soft-delete for non-
 * financial PII. Tunable via {@code app.retention.soft-delete-days}.
 * Financial records (rent invoices, payments) live in payment-service
 * with a separate 7-year retention window — those are NOT touched
 * here; the scheduler only purges {@code User} rows (and their
 * dependent {@code BankAccount} row, which is PII).
 *
 * <p>Runs daily at 03:00 server time by default. Configurable via
 * {@code app.retention.cron}. Set
 * {@code app.retention.enabled=false} to disable entirely (useful for
 * local dev so a manually soft-deleted test user doesn't vanish
 * overnight).
 *
 * <p>Each hard-deletion emits a {@code user.purged} audit row so the
 * security-ops index has a record of which rows were removed and
 * when — crucial for regulator inquiries that the platform actually
 * honoured the deletion request.
 */
@Component
@Slf4j
public class DataRetentionScheduler {

    private final UserRepo userRepo;
    private final BankAccountRepo bankAccountRepo;
    private final AuditEventPublisher audit;

    @Value("${app.retention.soft-delete-days:30}")
    private long softDeleteRetentionDays;

    @Value("${app.retention.enabled:true}")
    private boolean enabled;

    public DataRetentionScheduler(UserRepo userRepo,
                                  BankAccountRepo bankAccountRepo,
                                  AuditEventPublisher audit) {
        this.userRepo = userRepo;
        this.bankAccountRepo = bankAccountRepo;
        this.audit = audit;
    }

    /**
     * Daily sweep at 03:00 local time (low-traffic window).
     * {@code app.retention.cron} overrides for ops experimentation
     * (e.g. dry-run-friendly {@code "*\/15 * * * * *"} in dev).
     */
    @Scheduled(cron = "${app.retention.cron:0 0 3 * * *}")
    @Transactional
    public void purgeSoftDeleted() {
        if (!enabled) {
            log.debug("Retention sweep skipped — app.retention.enabled=false");
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(softDeleteRetentionDays);
        List<User> stale = userRepo.findSoftDeletedBefore(cutoff);
        if (stale.isEmpty()) {
            log.debug("Retention sweep: no rows eligible for purge (cutoff={})", cutoff);
            return;
        }
        log.info("Retention sweep: purging {} soft-deleted users older than {}",
                stale.size(), cutoff);
        for (User u : stale) {
            try {
                // Wipe the dependent BankAccount row first (PII). The
                // unique constraint on user_id means there's at most
                // one row to nuke; deleteByUserId is a no-op if absent.
                bankAccountRepo.deleteByUserId(u.getAuthUserId());
                userRepo.delete(u);
                audit.publishSuccess("user.purged",
                        "system-retention",
                        u.getAuthUserId(),
                        u.getId(),
                        Map.of(
                                "softDeletedAt", String.valueOf(u.getDeletedAt()),
                                "retentionDays", String.valueOf(softDeleteRetentionDays)));
                log.info("Purged user {} (soft-deleted {})", u.getId(), u.getDeletedAt());
            } catch (Exception ex) {
                // Don't let one bad row stop the sweep; the next run
                // will retry. Audit the failure so an investigation
                // can see WHY purging stalled.
                log.error("Retention sweep failed for user {}: {}",
                        u.getId(), ex.getMessage(), ex);
                audit.publishFailure("user.purged",
                        "system-retention",
                        u.getAuthUserId(),
                        ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }
}
