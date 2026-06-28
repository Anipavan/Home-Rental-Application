package com.spa.home_rental_application.auth_service.Service.Impul;

import com.spa.home_rental_application.auth_service.Dto.Response.MaintainerPaymentStatusResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.SystemSettingResponse;
import com.spa.home_rental_application.auth_service.Entity.SystemSetting;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Repository.SystemSettingRepository;
import com.spa.home_rental_application.auth_service.Service.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the global-toggle reads / writes plus the
 * maintainer-payment state machine.
 *
 * <p>Cache shape: one {@code AtomicReference<CachedFlag>} for the
 * single boolean toggle. We don't depend on Caffeine because we
 * only have one cached value and reach-for-Caffeine for that would
 * be over-engineered. TTL is 60 seconds — long enough to absorb
 * per-request status reads, short enough that an admin toggle flip
 * propagates in under a minute even if the {@link #invalidateCache}
 * call somehow got lost (defence in depth).
 */
@Service
@Slf4j
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private static final String KEY_MAINTAINER_PAYMENT = "maintainer_payment_enabled";
    private static final String KEY_EMAIL_VERIFICATION = "email_verification_required";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final SystemSettingRepository repo;
    private final BigDecimal registrationFeeInr;
    private final AtomicReference<CachedFlag> cache = new AtomicReference<>();
    private final AtomicReference<CachedFlag> emailVerifCache = new AtomicReference<>();

    public SystemSettingsServiceImpl(
            SystemSettingRepository repo,
            @Value("${app.maintainer-registration.fee-inr:999}") BigDecimal registrationFeeInr) {
        this.repo = repo;
        this.registrationFeeInr = registrationFeeInr;
    }

    /* ---------------- Toggle reads / writes ---------------- */

    @Override
    public boolean isMaintainerPaymentEnabled() {
        CachedFlag c = cache.get();
        if (c != null && c.fresh()) return c.value;
        // Miss or expired — re-read and refresh.
        boolean v = repo.findBySettingKey(KEY_MAINTAINER_PAYMENT)
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false); // default OFF when row is absent (first boot before V14 ran)
        cache.set(new CachedFlag(v, Instant.now()));
        return v;
    }

    @Override
    @Transactional
    public void setMaintainerPaymentEnabled(boolean enabled, Long adminUserId) {
        SystemSetting row = repo.findBySettingKey(KEY_MAINTAINER_PAYMENT)
                .orElseGet(() -> SystemSetting.builder()
                        .settingKey(KEY_MAINTAINER_PAYMENT)
                        .build());
        row.setValue(Boolean.toString(enabled));
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(adminUserId);
        repo.save(row);
        invalidateCache();
        log.info("system_settings updated: maintainer_payment_enabled={} by admin={}", enabled, adminUserId);
    }

    @Override
    public boolean isEmailVerificationRequired() {
        CachedFlag c = emailVerifCache.get();
        if (c != null && c.fresh()) return c.value;
        boolean v = repo.findBySettingKey(KEY_EMAIL_VERIFICATION)
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);
        emailVerifCache.set(new CachedFlag(v, Instant.now()));
        return v;
    }

    @Override
    @Transactional
    public void setEmailVerificationRequired(boolean required, Long adminUserId) {
        SystemSetting row = repo.findBySettingKey(KEY_EMAIL_VERIFICATION)
                .orElseGet(() -> SystemSetting.builder()
                        .settingKey(KEY_EMAIL_VERIFICATION)
                        .build());
        row.setValue(Boolean.toString(required));
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(adminUserId);
        repo.save(row);
        emailVerifCache.set(null);
        log.info("system_settings updated: email_verification_required={} by admin={}", required, adminUserId);
    }

    @Override
    public List<SystemSettingResponse> listAll() {
        return repo.findAll().stream()
                .map(s -> new SystemSettingResponse(
                        s.getSettingKey(), s.getValue(), s.getUpdatedAt(), s.getUpdatedBy()))
                .toList();
    }

    private void invalidateCache() {
        cache.set(null);
    }

    /* ---------------- State machine ---------------- */

    @Override
    public MaintainerPaymentStatusResponse computeStatus(UserDetails user) {
        Instant now = Instant.now();
        boolean gateOn = isMaintainerPaymentEnabled();
        boolean paid = user.getPaymentPaidAt() != null;

        // Branch 1: gate disabled OR user already paid/grandfathered.
        if (!gateOn || paid) {
            return new MaintainerPaymentStatusResponse(
                    MaintainerPaymentStatusResponse.Status.PAID,
                    null, null, null, registrationFeeInr);
        }

        // Trial window. Treat missing trial_started_at defensively:
        // a row that somehow has paid_at=null AND trial_started_at=null
        // is a state we should never persist, but if it happens we
        // default to "trial started now" so the user has 30 days
        // rather than instantly getting prompted.
        Instant trialStart = user.getPaymentTrialStartedAt() != null
                ? user.getPaymentTrialStartedAt()
                : (user.getRecordCreatedDate() != null
                        ? user.getRecordCreatedDate()
                        : now);
        Instant trialEnd = trialStart.plus(30, ChronoUnit.DAYS);
        if (now.isBefore(trialEnd)) {
            int daysLeft = (int) Math.max(1, ChronoUnit.DAYS.between(now, trialEnd));
            return new MaintainerPaymentStatusResponse(
                    MaintainerPaymentStatusResponse.Status.TRIAL,
                    daysLeft, 2, null, registrationFeeInr);
        }

        // Post-trial. Walk skip count.
        int skipCount = user.getPaymentSkipCount() == null ? 0 : user.getPaymentSkipCount();
        int skipsLeft = Math.max(0, 2 - skipCount);

        // No skips left → FORCED.
        if (skipsLeft == 0) {
            return new MaintainerPaymentStatusResponse(
                    MaintainerPaymentStatusResponse.Status.FORCED,
                    null, 0, null, registrationFeeInr);
        }

        // Skip(s) remaining. Are we still inside the 4-day grace
        // from the last skip? If so SKIP_GRACE; if grace expired or
        // never skipped, PROMPT.
        Instant lastSkip = user.getPaymentLastSkipAt();
        Instant graceEnd = lastSkip == null ? null : lastSkip.plus(4, ChronoUnit.DAYS);
        if (graceEnd != null && now.isBefore(graceEnd)) {
            return new MaintainerPaymentStatusResponse(
                    MaintainerPaymentStatusResponse.Status.SKIP_GRACE,
                    null, skipsLeft, graceEnd, registrationFeeInr);
        }

        return new MaintainerPaymentStatusResponse(
                MaintainerPaymentStatusResponse.Status.PROMPT,
                null, skipsLeft, null, registrationFeeInr);
    }

    /** Tiny TTL cache value. */
    private record CachedFlag(boolean value, Instant readAt) {
        boolean fresh() {
            return Instant.now().isBefore(readAt.plus(CACHE_TTL));
        }
    }
}
