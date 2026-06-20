package com.spa.home_rental_application.payment_service.payment_service.scheduler;

import com.spa.home_rental_application.payment_service.payment_service.client.AuthServiceFeign;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Safety net for the paid-registration flow. The happy-path call into
 * auth-service from {@code RegistrationPaymentServiceImpl.verify}
 * runs as soon as Razorpay confirms PAID. If that Feign call fails
 * (auth-service rolling restart, network glitch, transient 5xx) the
 * Payment row stays PAID but the auth row stays disabled — the user
 * paid but can't log in.
 *
 * <p>This scheduler sweeps every 5 minutes, looking back over the
 * last {@code app.maintainer-registration.reconciler.lookback-minutes}
 * window for PAID {@code MAINTAINER_REGISTRATION} payments, and
 * re-issues the activation Feign for each. {@code activateRegistration}
 * is idempotent on the auth-service side — calling it for an
 * already-enabled user is a logged no-op, so re-running for
 * already-reconciled payments is safe.
 */
@Component
@Slf4j
public class RegistrationActivationReconciler {

    private final PaymentRepository paymentRepo;
    private final AuthServiceFeign authServiceFeign;
    private final int lookbackMinutes;

    public RegistrationActivationReconciler(PaymentRepository paymentRepo,
                                             AuthServiceFeign authServiceFeign,
                                             @Value("${app.maintainer-registration.reconciler.lookback-minutes:60}") int lookbackMinutes) {
        this.paymentRepo = paymentRepo;
        this.authServiceFeign = authServiceFeign;
        this.lookbackMinutes = lookbackMinutes;
    }

    @Scheduled(initialDelay = 120_000L, fixedDelay = 300_000L)
    public void sweep() {
        Instant since = Instant.now().minus(lookbackMinutes, ChronoUnit.MINUTES);
        List<Payment> payments = paymentRepo.findRecentPaidRegistrationPayments(since);
        if (payments.isEmpty()) {
            log.debug("RegistrationActivationReconciler — nothing to reconcile in last {}m", lookbackMinutes);
            return;
        }
        log.info("RegistrationActivationReconciler considering {} PAID registration payment(s)",
                payments.size());
        int attempted = 0;
        int failed = 0;
        for (Payment p : payments) {
            try {
                Long authUserId = Long.valueOf(p.getTenantId());
                authServiceFeign.activateRegistration(authUserId,
                        Map.of("paymentId", p.getId()));
                attempted++;
            } catch (NumberFormatException nfe) {
                log.warn("Reconciler skipped paymentId={}: tenantId not a Long ({})",
                        p.getId(), p.getTenantId());
            } catch (Exception ex) {
                // Feign failed — try again on next tick. activateRegistration
                // is idempotent, so neither over- nor under-counting risks
                // double-activation.
                failed++;
                log.warn("Reconciler activation failed for paymentId={} authUserId={}: {}",
                        p.getId(), p.getTenantId(), ex.getMessage());
            }
        }
        log.info("RegistrationActivationReconciler done — attempted={} failed={}", attempted, failed);
    }
}
