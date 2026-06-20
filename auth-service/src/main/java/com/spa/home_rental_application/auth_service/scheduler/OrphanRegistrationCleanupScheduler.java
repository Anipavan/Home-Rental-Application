package com.spa.home_rental_application.auth_service.scheduler;

import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Periodically deletes auth rows that started the paid-maintainer
 * signup, were persisted as {@code enabled=false,
 * disable_reason='REGISTRATION_PAYMENT_PENDING'}, and never made it
 * to {@code activateRegistration}. Without this sweep, a user who
 * closed their browser before paying would be stuck — the email /
 * phone / userName uniqueness constraints would block them from
 * re-registering forever, and login would just bounce off the
 * paywall every time.
 *
 * <p>The window is configurable via
 * {@code app.maintainer-registration.cleanup.expiry-hours} (default
 * 24). A 24-hour grace is long enough to cover "I'll pay tomorrow"
 * UX, but short enough that abandoned rows don't accumulate.
 *
 * <p>Companion to payment-service's {@code StalePaymentExpiryScheduler}
 * (which marks PENDING payments as FAILED after 30 minutes): this
 * scheduler cleans up the *auth* side, the payment scheduler the
 * *payment* side. They run independently — neither blocks the other.
 */
@Component
@Slf4j
public class OrphanRegistrationCleanupScheduler {

    private final UserRepository userRepository;
    private final int expiryHours;

    public OrphanRegistrationCleanupScheduler(
            UserRepository userRepository,
            @Value("${app.maintainer-registration.cleanup.expiry-hours:24}") int expiryHours) {
        this.userRepository = userRepository;
        this.expiryHours = expiryHours;
    }

    /**
     * Run hourly (and once at startup, 60 s after boot, so an ops
     * person can verify the sweep is wired without waiting an hour).
     */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 3_600_000L)
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(expiryHours * 3600L);
        int deleted = userRepository.deleteOrphanedPaymentPendingRows(cutoff);
        if (deleted > 0) {
            log.info("OrphanRegistrationCleanupScheduler removed {} stale paywall row(s) older than {}h",
                    deleted, expiryHours);
        } else {
            log.debug("OrphanRegistrationCleanupScheduler — no orphan rows older than {}h", expiryHours);
        }
    }
}
