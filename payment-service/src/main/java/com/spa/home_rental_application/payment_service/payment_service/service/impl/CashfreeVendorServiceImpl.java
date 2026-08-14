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

        // ACTIVE / IN_BANK_VALIDATION path — this used to be a hard
        // early-return, but that meant editing bank details in the
        // profile UI silently diverged from the destination Cashfree
        // was actually routing money to. Now: fetch the CURRENT bank,
        // compare against what's stored locally, and if the tuple
        // differs, PATCH the Cashfree vendor with the new bank and
        // reset local status to IN_BANK_VALIDATION until Cashfree's
        // penny-drop re-verifies. Unchanged banks still short-circuit.
        if (row.getStatus() == CashfreeVendorStatus.ACTIVE
                || row.getStatus() == CashfreeVendorStatus.IN_BANK_VALIDATION) {
            return syncBankIfChanged(userId, row);
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

    /**
     * Reconcile a live Cashfree vendor with the user's current bank.
     * If the bank hasn't changed since the last successful
     * registration we no-op (the debounced Kafka event storm on
     * every user-service save would otherwise hammer Cashfree). If it
     * has changed we call {@code updateVendor} which PATCHes the
     * vendor and re-triggers penny-drop, dropping local status to
     * {@code IN_BANK_VALIDATION} until it settles.
     */
    private Optional<CashfreeVendor> syncBankIfChanged(String userId, CashfreeVendor row) {
        UserClient.BankAccountInternal bank = safeFetchBank(userId);
        if (bank == null
                || bank.accountNumber() == null || bank.accountNumber().isBlank()
                || bank.ifscCode() == null || bank.ifscCode().isBlank()) {
            // Bank details vanished (user cleared them?) — leave the
            // Cashfree vendor as-is so existing payouts keep working.
            // A real "vendor should stop accepting money" flow needs
            // an explicit admin action, not an implicit inference.
            log.debug("Vendor userId={} is {} but no bank on file to compare; leaving unchanged",
                    userId, row.getStatus());
            return Optional.of(repo.save(row));
        }
        String newLast4 = bank.accountNumber().length() >= 4
                ? bank.accountNumber().substring(bank.accountNumber().length() - 4)
                : bank.accountNumber();
        boolean same = java.util.Objects.equals(newLast4, row.getBankAccountLast4())
                && java.util.Objects.equals(bank.ifscCode(), row.getBankIfsc())
                && java.util.Objects.equals(bank.accountHolderName(), row.getBankAccountHolder());
        if (same) {
            log.debug("Vendor userId={} bank unchanged (last4={} ifsc={}), no-op",
                    userId, newLast4, bank.ifscCode());
            return Optional.of(repo.save(row));
        }

        log.info("Vendor userId={} bank changed (old last4={} ifsc={} → new last4={} ifsc={}), issuing PATCH",
                userId, row.getBankAccountLast4(), row.getBankIfsc(), newLast4, bank.ifscCode());

        if (!"cashfree".equalsIgnoreCase(activeGateway)
                || !(paymentGateway instanceof CashfreePaymentGateway cashfree)) {
            // Mock-mode: just mirror the new bank locally, skip API.
            row.setBankAccountHolder(bank.accountHolderName());
            row.setBankIfsc(bank.ifscCode());
            row.setBankAccountLast4(newLast4);
            log.info("Vendor userId={} bank mirrored locally (gateway=mock, skipping Cashfree PATCH)", userId);
            return Optional.of(repo.save(row));
        }

        // Real gateway path — fetch KYC too so we can send the same
        // shape the create path used (email / phone / name recomputed
        // in case those also drifted). PAN can't change on Cashfree's
        // side once ACTIVE; passing whatever's stored is a no-op there.
        KycClient.KycInternal kyc = safeFetchKyc(userId);
        String panHolder = kyc == null ? null : kyc.panHolderName();
        String panNumber = kyc == null ? null : kyc.panNumber();
        String email = safeUserEmail(userId, panHolder);
        String phone = "9999999999";

        try {
            var result = cashfree.updateVendor(
                    new CashfreePaymentGateway.CashfreeVendorRegistrationRequest(
                            row.getCashfreeVendorId(),
                            firstNonBlank(panHolder, bank.accountHolderName(), userId),
                            email,
                            phone,
                            panNumber,
                            bank.accountNumber(),
                            bank.accountHolderName(),
                            bank.ifscCode(),
                            null
                    )
            );
            row.setBankAccountHolder(bank.accountHolderName());
            row.setBankIfsc(bank.ifscCode());
            row.setBankAccountLast4(newLast4);
            // Reflect Cashfree's returned status (typically flips to
            // IN_BANK_VALIDATION until the new penny-drop clears);
            // fall back to IN_BANK_VALIDATION if the response is thin.
            CashfreeVendorStatus newStatus = mapCashfreeStatus(result.status());
            if (newStatus == CashfreeVendorStatus.REGISTERING) {
                newStatus = CashfreeVendorStatus.IN_BANK_VALIDATION;
            }
            row.setStatus(newStatus);
            row.setFailureReason(null);
            row.setLastAttemptedAt(Instant.now());
        } catch (Exception ex) {
            log.warn("Cashfree updateVendor failed for userId={}", userId, ex);
            // Keep the ACTIVE status — the old bank still works, so
            // payments don't hard-fail. Surface the failure via the
            // failureReason column so admins can spot the divergence.
            row.setFailureReason(safeTruncate("Bank update failed: " + ex.getMessage(), 1900));
        }
        return Optional.of(repo.save(row));
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
