package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.payment_service.payment_service.client.KycClient;
import com.spa.home_rental_application.payment_service.payment_service.client.UserClient;
import com.spa.home_rental_application.payment_service.payment_service.entities.CashfreeVendor;
import com.spa.home_rental_application.payment_service.payment_service.enums.CashfreeVendorStatus;
import com.spa.home_rental_application.payment_service.payment_service.gateway.CashfreePaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.repository.CashfreeVendorRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.CashfreeVendorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Owner-vendor lifecycle orchestrator.
 *
 * <p>Wired to both Kafka triggers (bank-saved + kyc-verified) via
 * {@code CashfreeVendorEventListener}. Both funnel into
 * {@link #tryRegisterIfReady} which:
 *
 * <ol>
 *   <li>Fetches the owner's bank + KYC via Feign — either can be null
 *       (user-service / kyc-service down) or empty (user hasn't done
 *       that half yet). Handled explicitly, not by exception.</li>
 *   <li>If both prereqs are present, calls
 *       {@link CashfreePaymentGateway#registerVendor} and updates the
 *       row to {@code IN_BANK_VALIDATION} (Cashfree's async penny-drop
 *       state). A separate webhook / poll flips it to {@code ACTIVE}
 *       when the drop succeeds.</li>
 *   <li>Otherwise, saves the partial state
 *       ({@code PENDING_KYC} / {@code PENDING_BANK}) so the next
 *       Kafka trigger can complete it.</li>
 * </ol>
 *
 * <p>Only fires the real Cashfree call when {@code app.payment.gateway
 * = cashfree} — under {@code mock} the row's state still advances
 * through the prereq checks (so admins can see who's ready without
 * a real registration) but the registerVendor() call is skipped.
 */
@Service
@Slf4j
public class CashfreeVendorServiceImpl implements CashfreeVendorService {

    /** Cap so a permanently-broken vendor can't hammer Cashfree forever. */
    private static final int MAX_ATTEMPTS = 5;

    private final CashfreeVendorRepository repo;
    private final UserClient userClient;
    private final KycClient kycClient;
    private final PaymentGateway paymentGateway;
    private final String activeGateway;

    public CashfreeVendorServiceImpl(CashfreeVendorRepository repo,
                                      UserClient userClient,
                                      KycClient kycClient,
                                      PaymentGateway paymentGateway,
                                      @Value("${app.payment.gateway:mock}") String activeGateway) {
        this.repo = repo;
        this.userClient = userClient;
        this.kycClient = kycClient;
        this.paymentGateway = paymentGateway;
        this.activeGateway = activeGateway;
    }

    /* ---------- public API ---------- */

    @Override
    @Transactional
    public Optional<CashfreeVendor> tryRegisterIfReady(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        CashfreeVendor row = repo.findByUserId(userId)
                .orElseGet(() -> CashfreeVendor.builder()
                        .userId(userId)
                        .status(CashfreeVendorStatus.PENDING_KYC)
                        .attemptCount(0)
                        .build());

        // Terminal states — don't touch. Admin uses reRegister() to
        // force a retry from REJECTED / FAILED.
        if (row.getStatus() == CashfreeVendorStatus.ACTIVE
                || row.getStatus() == CashfreeVendorStatus.IN_BANK_VALIDATION) {
            log.debug("Vendor for userId={} already at status={}, skipping",
                    userId, row.getStatus());
            return Optional.of(repo.save(row));
        }
        if (row.getAttemptCount() != null && row.getAttemptCount() >= MAX_ATTEMPTS
                && row.getStatus() == CashfreeVendorStatus.FAILED) {
            log.warn("Vendor for userId={} exceeded MAX_ATTEMPTS ({}), needs admin re-register",
                    userId, MAX_ATTEMPTS);
            return Optional.of(repo.save(row));
        }

        UserClient.BankAccountInternal bank = safeFetchBank(userId);
        KycClient.KycInternal kyc          = safeFetchKyc(userId);

        boolean bankReady = bank != null
                && bank.accountNumber() != null && !bank.accountNumber().isBlank()
                && bank.ifscCode() != null && !bank.ifscCode().isBlank();
        boolean kycReady  = kyc != null
                && Boolean.TRUE.equals(kyc.verified())
                && kyc.panNumber() != null && !kyc.panNumber().isBlank();

        // Update the row with what we know so the admin dashboard shows
        // useful partial state even before both prereqs land.
        if (bank != null) {
            row.setBankAccountHolder(bank.accountHolderName());
            row.setBankIfsc(bank.ifscCode());
            String acc = bank.accountNumber();
            row.setBankAccountLast4(acc == null ? null
                    : acc.length() >= 4 ? acc.substring(acc.length() - 4) : acc);
        }

        if (!bankReady) {
            row.setStatus(CashfreeVendorStatus.PENDING_BANK);
            row.setFailureReason(null);
            log.info("Vendor userId={} → PENDING_BANK (bankReady=false)", userId);
            return Optional.of(repo.save(row));
        }
        if (!kycReady) {
            row.setStatus(CashfreeVendorStatus.PENDING_KYC);
            row.setFailureReason(null);
            log.info("Vendor userId={} → PENDING_KYC (kycReady=false)", userId);
            return Optional.of(repo.save(row));
        }

        // Both prereqs met — attempt registration.
        row.setStatus(CashfreeVendorStatus.REGISTERING);
        row.setAttemptCount(row.getAttemptCount() == null ? 1 : row.getAttemptCount() + 1);
        row.setLastAttemptedAt(Instant.now());
        row = repo.save(row);

        if (!"cashfree".equalsIgnoreCase(activeGateway)
                || !(paymentGateway instanceof CashfreePaymentGateway cashfree)) {
            // Mock-mode: pretend we called Cashfree, park the row
            // at ACTIVE so admins see a completed lifecycle in dev.
            log.info("Vendor userId={} would register with Cashfree (gateway=mock, skipping API call)",
                    userId);
            row.setCashfreeVendorId(userId);
            row.setStatus(CashfreeVendorStatus.ACTIVE);
            row.setActivatedAt(Instant.now());
            return Optional.of(repo.save(row));
        }

        // Real gateway path.
        String userProfileEmail = safeUserEmail(userId, kyc.panHolderName());
        String phone = "9999999999"; // Owner profile doesn't currently expose phone
                                     // to payment-service; sandbox accepts any digits.
                                     // TODO: extend UserClient with getPhone() in a
                                     // follow-up once we have live users needing SMS
                                     // notifications from Cashfree itself.
        try {
            var result = cashfree.registerVendor(
                    new CashfreePaymentGateway.CashfreeVendorRegistrationRequest(
                            userId,
                            firstNonBlank(kyc.panHolderName(), bank.accountHolderName(), userId),
                            userProfileEmail,
                            phone,
                            kyc.panNumber(),
                            bank.accountNumber(),
                            bank.accountHolderName(),
                            bank.ifscCode(),
                            null  // → adapter defaults to "Miscellaneous"
                    )
            );
            row.setCashfreeVendorId(result.cashfreeVendorId());
            row.setStatus(mapCashfreeStatus(result.status()));
            if (row.getStatus() == CashfreeVendorStatus.ACTIVE) {
                row.setActivatedAt(Instant.now());
            }
            row.setFailureReason(null);
            log.info("Vendor userId={} registered with Cashfree — cfVendorId={} status={}",
                    userId, result.cashfreeVendorId(), result.status());
        } catch (Exception ex) {
            log.warn("Cashfree registerVendor failed for userId={}", userId, ex);
            row.setStatus(CashfreeVendorStatus.FAILED);
            row.setFailureReason(safeTruncate(ex.getMessage(), 1900));
        }
        return Optional.of(repo.save(row));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashfreeVendor> getForUser(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        return repo.findByUserId(userId);
    }

    @Override
    @Transactional
    public Optional<CashfreeVendor> reRegister(String userId) {
        repo.findByUserId(userId).ifPresent(r -> {
            r.setAttemptCount(0);
            r.setFailureReason(null);
            // Force one of the pending states so tryRegisterIfReady
            // doesn't short-circuit on the ACTIVE guard.
            if (r.getStatus() == CashfreeVendorStatus.ACTIVE
                    || r.getStatus() == CashfreeVendorStatus.IN_BANK_VALIDATION) {
                // Nothing to retry on a good row — no-op.
                return;
            }
            r.setStatus(CashfreeVendorStatus.PENDING_BANK);
            repo.save(r);
        });
        return tryRegisterIfReady(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashfreeVendor> listAll() {
        return repo.findByStatusInOrderByLastAttemptedAtAsc(List.of(
                CashfreeVendorStatus.PENDING_KYC,
                CashfreeVendorStatus.PENDING_BANK,
                CashfreeVendorStatus.REGISTERING,
                CashfreeVendorStatus.IN_BANK_VALIDATION,
                CashfreeVendorStatus.ACTIVE,
                CashfreeVendorStatus.REJECTED,
                CashfreeVendorStatus.FAILED
        ));
    }

    /* ---------- helpers ---------- */

    private UserClient.BankAccountInternal safeFetchBank(String userId) {
        try {
            return userClient.getBankAccountInternal(userId);
        } catch (Exception ex) {
            // 404 (no bank yet) surfaces here as an exception from
            // Feign — treat as "not ready" rather than "outage".
            log.debug("No bank details for userId={} (or user-service error): {}",
                    userId, ex.getMessage());
            return null;
        }
    }

    private KycClient.KycInternal safeFetchKyc(String userId) {
        try {
            return kycClient.getInternal(userId);
        } catch (Exception ex) {
            log.debug("No KYC record for userId={} (or kyc-service error): {}",
                    userId, ex.getMessage());
            return null;
        }
    }

    /**
     * Cashfree returns a small set of status strings; map them onto
     * our own enum. Unknown values default to REGISTERING (safe —
     * the next status-poll will resolve it).
     */
    private static CashfreeVendorStatus mapCashfreeStatus(String status) {
        if (status == null) return CashfreeVendorStatus.REGISTERING;
        return switch (status.toUpperCase()) {
            case "ACTIVE"               -> CashfreeVendorStatus.ACTIVE;
            case "IN_BANK_VALIDATION"   -> CashfreeVendorStatus.IN_BANK_VALIDATION;
            case "REJECTED", "BLOCKED", "DELETED"
                                        -> CashfreeVendorStatus.REJECTED;
            default                     -> CashfreeVendorStatus.REGISTERING;
        };
    }

    private String safeUserEmail(String userId, String fallbackName) {
        try {
            var profile = userClient.getByAuthUserId(userId);
            if (profile != null && profile.email() != null && !profile.email().isBlank()) {
                return profile.email();
            }
        } catch (Exception ignored) { /* fall through */ }
        // Cashfree requires SOMETHING here; synthesise a placeholder so
        // registration doesn't fail on a missing email.
        return "owner-" + userId + "@anirudhhomes.in";
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return "Owner";
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
