package com.spa.home_rental_application.payment_service.payment_service.scheduler;

import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Auto-fails Payment rows that have been sitting in PENDING /
 * PROCESSING for too long without completing through the gateway.
 *
 * <p><b>Why this exists.</b> The Razorpay flow goes:
 * <ol>
 *   <li>FE calls /payments/initiate → status flips PENDING → PROCESSING</li>
 *   <li>User redirected to Razorpay</li>
 *   <li>Razorpay either succeeds (webhook + verify marks PAID) or
 *       fails (verify marks FAILED).</li>
 * </ol>
 *
 * <p>But users <em>abandon</em> step 3 all the time — close the tab,
 * Razorpay times out, network drops, the bank simulator silently
 * declines without redirecting back, BAD_REQUEST_ERROR with no
 * follow-up. Each abandon leaves a Payment stuck in PENDING /
 * PROCESSING forever. The tenant's Payments page accumulates "Due now"
 * entries for charges they've already paid through other attempts.
 *
 * <p>This sweep finds those zombies and marks them FAILED so they drop
 * off the "Due now" surface. {@code failure_reason} is filled so
 * support can tell genuine gateway failures apart from "user walked
 * away" timeouts.
 *
 * <p><b>Cutoff = 30 minutes from {@code updated_at}</b>. Any /initiate
 * or /verify touch resets the clock (updated_at = now), so a user
 * actively retrying a payment never gets prematurely expired. Only
 * truly idle rows are caught.
 *
 * <p>Sweep runs every 5 minutes. Idempotent — marking a FAILED row
 * FAILED again is a no-op (filter excludes FAILED status).
 */
@Component
@Slf4j
public class StalePaymentExpiryScheduler {

    private static final long IDLE_MINUTES_BEFORE_EXPIRY = 30;
    private static final long SWEEP_INTERVAL_MS = 5 * 60 * 1000L;

    private final PaymentRepository paymentRepo;

    public StalePaymentExpiryScheduler(PaymentRepository paymentRepo) {
        this.paymentRepo = paymentRepo;
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    @Transactional
    public void expireStalePayments() {
        Instant cutoff = Instant.now().minus(IDLE_MINUTES_BEFORE_EXPIRY, ChronoUnit.MINUTES);
        List<Payment> stale = paymentRepo.findByStatusInAndUpdatedAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING),
                cutoff);

        if (stale.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (Payment p : stale) {
            p.setStatus(PaymentStatus.FAILED);
            // Preserve any pre-existing failureReason — the gateway may
            // have set one (e.g. webhook ran before this sweep) and that
            // detail is more useful than our generic "timed out" message.
            if (p.getFailureReason() == null || p.getFailureReason().isBlank()) {
                p.setFailureReason("Payment session timed out (no completion within "
                        + IDLE_MINUTES_BEFORE_EXPIRY + " minutes).");
            }
            p.setUpdatedAt(now);
        }
        paymentRepo.saveAll(stale);

        log.info("StalePaymentExpiryScheduler: marked {} payment(s) FAILED after {}-minute idle timeout",
                stale.size(), IDLE_MINUTES_BEFORE_EXPIRY);
    }
}
