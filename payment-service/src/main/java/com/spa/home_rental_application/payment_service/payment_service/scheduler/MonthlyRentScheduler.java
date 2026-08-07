package com.spa.home_rental_application.payment_service.payment_service.scheduler;

import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly rent-invoice generator.
 *
 * <p>Before this class landed, rent was seeded exactly once by
 * {@code FlatOccupiedListener.onMessage} → {@code paymentService.onFlatOccupied}
 * when the {@code flat.occupied} Kafka event fired at lease-start
 * time. After month 1, no new invoices were ever created — the
 * tenant paid once, then the "Due now" section on their Payments page
 * went permanently empty, and the owner had to manually create every
 * subsequent month's invoice via the admin UI. That was Issue #day-30
 * waiting to happen in production.
 *
 * <p>This scheduler closes the loop:
 * <ol>
 *   <li>Runs at 01:15 IST on the 1st of every month.</li>
 *   <li>Finds every flat with a PAID payment in the last 45 days —
 *       proxy for "there's an active tenant here who's been paying".</li>
 *   <li>For each such flat, skips if there's already a PENDING /
 *       OVERDUE / PROCESSING invoice covering this month (idempotent
 *       re-runs) OR later.</li>
 *   <li>Otherwise mints a new PENDING Payment cloning the latest paid
 *       invoice's tenantId / ownerId / amount, with {@code dueDate}
 *       = 1st of the current month.</li>
 * </ol>
 *
 * <p>What this deliberately does NOT do:
 * <ul>
 *   <li><b>Freshly-vacated flats</b> — no PAID activity in 45+ days
 *       → no invoice. Owner needs to trigger the next tenant's flow
 *       via the assign-tenant UI, which fires {@code flat.occupied}
 *       again.</li>
 *   <li><b>Rent-amount changes</b> — the amount comes from the
 *       latest PAID invoice, which is the last agreed rent. If the
 *       owner is raising rent for the coming month they should edit
 *       the auto-created invoice's amount before the tenant pays,
 *       or delete + recreate.</li>
 *   <li><b>Late-fee compounding</b> — that's the {@link LateFeeScheduler}'s
 *       job. We just create clean PENDING rows; late-fee promotion
 *       happens 06:00 the same day if they're still unpaid past
 *       their due date.</li>
 * </ul>
 *
 * <p>Idempotent — re-running on the same day is safe. Manual trigger
 * for ad-hoc use: call {@link #seedMonthlyRent()} via actuator /
 * {@code /actuator/scheduledtasks} probing.
 */
@Component
@Slf4j
public class MonthlyRentScheduler {

    /** Grace window for "recent PAID activity" — a flat needs at least
     *  one PAID payment in the last {@code RECENT_PAID_DAYS} days to
     *  qualify for auto-billing. 45 days gives 15 days grace over a
     *  monthly cycle for tenants who paid late last month. */
    private static final int RECENT_PAID_DAYS = 45;

    private final PaymentRepository repo;

    public MonthlyRentScheduler(PaymentRepository repo) {
        this.repo = repo;
    }

    /**
     * 01:15 IST on the 1st of every month. Offset from
     * {@link LateFeeScheduler} (06:00) so the two never race.
     * Cron overridable via {@code app.payment.monthly-rent.cron}.
     */
    @Scheduled(cron = "${app.payment.monthly-rent.cron:0 15 1 1 * *}")
    @Transactional
    public void seedMonthlyRent() {
        LocalDate today = LocalDate.now();
        LocalDate targetDue = today.withDayOfMonth(1);
        LocalDate cutoff = today.minusDays(RECENT_PAID_DAYS);

        List<Payment> recentPaid = repo.findRecentPaidPayments(cutoff);
        if (recentPaid.isEmpty()) {
            log.info("MonthlyRentScheduler: no PAID payments in the last {} days — nothing to do",
                    RECENT_PAID_DAYS);
            return;
        }

        // The query returns newest-first, so putIfAbsent naturally
        // captures the latest per flatId.
        Map<String, Payment> latestPerFlat = new LinkedHashMap<>();
        for (Payment p : recentPaid) {
            if (p.getFlatId() != null) {
                latestPerFlat.putIfAbsent(p.getFlatId(), p);
            }
        }

        int created = 0, skipped = 0;
        for (Payment latest : latestPerFlat.values()) {
            // Idempotency guard — skip if any active invoice already
            // covers this month or beyond. Catches:
            //   • scheduler re-run on the same day
            //   • owner already manually created this month's invoice
            //   • flat.occupied handler seeded a future month already
            List<Payment> active = repo.findByFlatIdAndStatusIn(
                    latest.getFlatId(),
                    List.of(PaymentStatus.PENDING,
                            PaymentStatus.OVERDUE,
                            PaymentStatus.PROCESSING));
            boolean alreadyBilled = active.stream()
                    .anyMatch(p -> p.getDueDate() != null
                            && !p.getDueDate().isBefore(targetDue));
            if (alreadyBilled) {
                skipped++;
                continue;
            }

            BigDecimal rent = latest.getAmount() == null
                    ? BigDecimal.ZERO
                    : latest.getAmount();
            if (rent.signum() <= 0) {
                log.warn("MonthlyRentScheduler: skipping flat={} — latest paid rent was {}",
                        latest.getFlatId(), rent);
                skipped++;
                continue;
            }

            Payment next = Payment.builder()
                    .tenantId(latest.getTenantId())
                    .flatId(latest.getFlatId())
                    .ownerId(latest.getOwnerId())
                    .amount(rent)
                    .lateFee(BigDecimal.ZERO)
                    .totalAmount(rent)
                    .dueDate(targetDue)
                    .status(PaymentStatus.PENDING)
                    .build();
            repo.save(next);
            created++;
        }

        log.info("MonthlyRentScheduler: complete (considered={}, created={}, skipped={})",
                latestPerFlat.size(), created, skipped);
    }
}
