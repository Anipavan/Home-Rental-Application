package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient;
import com.spa.home_rental_application.payment_service.payment_service.entities.SocietyCashfreeVendor;
import com.spa.home_rental_application.payment_service.payment_service.enums.CashfreeVendorStatus;
import com.spa.home_rental_application.payment_service.payment_service.gateway.CashfreePaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.repository.SocietyCashfreeVendorRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.SocietyCashfreeVendorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Society-vendor lifecycle orchestrator. Mirror of
 * {@code CashfreeVendorServiceImpl} but keyed on {@code buildingId}
 * instead of {@code userId}, and pulls the bank + KYC from
 * property-service's society config (not user-service's bank_accounts).
 * The Cashfree vendor id is namespaced as {@code "society_" + buildingId}
 * so it can't collide with per-user vendor ids.
 */
@Service
@Slf4j
public class SocietyCashfreeVendorServiceImpl implements SocietyCashfreeVendorService {

    private static final int MAX_ATTEMPTS = 5;

    /** Sandbox-safe default. See cashfree-api-quirks memory —
     *  "Real Estate, Housing, Rentals" is the value Cashfree accepts
     *  for society-type vendors on both sandbox and production. */
    private static final String DEFAULT_BUSINESS_TYPE = "Real Estate, Housing, Rentals";

    private final SocietyCashfreeVendorRepository repo;
    private final PropertyClient propertyClient;
    private final PaymentGateway paymentGateway;
    private final String activeGateway;

    public SocietyCashfreeVendorServiceImpl(SocietyCashfreeVendorRepository repo,
                                             PropertyClient propertyClient,
                                             PaymentGateway paymentGateway,
                                             @Value("${app.payment.gateway:mock}") String activeGateway) {
        this.repo = repo;
        this.propertyClient = propertyClient;
        this.paymentGateway = paymentGateway;
        this.activeGateway = activeGateway;
    }

    @Override
    @Transactional
    public Optional<SocietyCashfreeVendor> tryRegisterIfReadyForSociety(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) return Optional.empty();

        SocietyCashfreeVendor row = repo.findByBuildingId(buildingId)
                .orElseGet(() -> SocietyCashfreeVendor.builder()
                        .buildingId(buildingId)
                        .status(CashfreeVendorStatus.PENDING_KYC)
                        .attemptCount(0)
                        .build());

        // Attempts-cap on repeated FAILED — only admin re-register
        // can push past this. Prevents a stuck vendor from hammering
        // Cashfree in a Kafka retry loop.
        if (row.getAttemptCount() != null && row.getAttemptCount() >= MAX_ATTEMPTS
                && row.getStatus() == CashfreeVendorStatus.FAILED) {
            log.warn("Society vendor buildingId={} exceeded MAX_ATTEMPTS ({}), needs admin re-register",
                    buildingId, MAX_ATTEMPTS);
            return Optional.of(repo.save(row));
        }

        PropertyClient.SocietyBankDetails bank = safeFetchBank(buildingId);
        if (bank == null) {
            log.info("Society vendor buildingId={} — property-service unavailable, deferring", buildingId);
            row.setStatus(CashfreeVendorStatus.PENDING_BANK);
            return Optional.of(repo.save(row));
        }

        // Denormalise the society_config_id for correlation.
        if (row.getSocietyConfigId() == null && bank.societyConfigId() != null) {
            row.setSocietyConfigId(bank.societyConfigId());
        }

        boolean bankReady = notBlank(bank.accountNumber()) && notBlank(bank.ifscCode())
                && notBlank(bank.accountHolder());
        boolean kycReady = notBlank(bank.panNumber()) && notBlank(bank.contactPhone())
                && notBlank(bank.contactEmail());

        // Update the row with what we know so admins see useful state.
        if (notBlank(bank.accountHolder())) row.setBankAccountHolder(bank.accountHolder());
        if (notBlank(bank.ifscCode())) row.setBankIfsc(bank.ifscCode());
        if (notBlank(bank.accountNumber())) {
            String acc = bank.accountNumber();
            row.setBankAccountLast4(acc.length() >= 4 ? acc.substring(acc.length() - 4) : acc);
        }

        if (!bankReady) {
            row.setStatus(CashfreeVendorStatus.PENDING_BANK);
            row.setFailureReason(null);
            log.info("Society vendor buildingId={} → PENDING_BANK", buildingId);
            return Optional.of(repo.save(row));
        }
        if (!kycReady) {
            row.setStatus(CashfreeVendorStatus.PENDING_KYC);
            row.setFailureReason(null);
            log.info("Society vendor buildingId={} → PENDING_KYC", buildingId);
            return Optional.of(repo.save(row));
        }

        // Both prereqs met. Decide between create vs update:
        //   - No cashfreeVendorId yet → fresh register
        //   - Already registered → compare bank tuple; if changed,
        //     issue PATCH via CashfreePaymentGateway.updateVendor
        String vendorId = row.getCashfreeVendorId();
        if (vendorId == null || vendorId.isBlank()) {
            vendorId = "society_" + buildingId;
        }

        boolean alreadyRegistered = row.getStatus() == CashfreeVendorStatus.ACTIVE
                || row.getStatus() == CashfreeVendorStatus.IN_BANK_VALIDATION;

        row.setAttemptCount(row.getAttemptCount() == null ? 1 : row.getAttemptCount() + 1);
        row.setLastAttemptedAt(Instant.now());

        if (!"cashfree".equalsIgnoreCase(activeGateway)
                || !(paymentGateway instanceof CashfreePaymentGateway cashfree)) {
            // Mock-mode: park at ACTIVE for dev flows.
            log.info("Society vendor buildingId={} would register with Cashfree (gateway=mock, skipping)",
                    buildingId);
            row.setCashfreeVendorId(vendorId);
            row.setStatus(CashfreeVendorStatus.ACTIVE);
            row.setActivatedAt(Instant.now());
            return Optional.of(repo.save(row));
        }

        String businessType = notBlank(bank.businessType()) ? bank.businessType()
                : DEFAULT_BUSINESS_TYPE;

        try {
            CashfreePaymentGateway.CashfreeVendorRegistrationRequest req =
                    new CashfreePaymentGateway.CashfreeVendorRegistrationRequest(
                            vendorId,
                            firstNonBlank(bank.accountHolder(), bank.societyDisplayName(), "Society " + buildingId),
                            bank.contactEmail(),
                            bank.contactPhone(),
                            bank.panNumber(),
                            bank.accountNumber(),
                            bank.accountHolder(),
                            bank.ifscCode(),
                            businessType
                    );
            CashfreePaymentGateway.CashfreeVendorRegistrationResult result =
                    alreadyRegistered ? cashfree.updateVendor(req) : cashfree.registerVendor(req);

            row.setCashfreeVendorId(result.cashfreeVendorId());
            row.setStatus(mapCashfreeStatus(result.status()));
            if (row.getStatus() == CashfreeVendorStatus.ACTIVE) {
                row.setActivatedAt(Instant.now());
            }
            row.setFailureReason(null);
            log.info("Society vendor buildingId={} {} — cfVendorId={} status={}",
                    buildingId, alreadyRegistered ? "updated" : "registered",
                    result.cashfreeVendorId(), result.status());
        } catch (Exception ex) {
            log.warn("Cashfree {} failed for society buildingId={}",
                    alreadyRegistered ? "updateVendor" : "registerVendor", buildingId, ex);
            // Preserve prior status on update-failure so payouts keep
            // routing to the old bank; on fresh-registration failure
            // mark FAILED so the admin dashboard surfaces it.
            if (!alreadyRegistered) {
                row.setStatus(CashfreeVendorStatus.FAILED);
            }
            row.setFailureReason(safeTruncate(ex.getMessage(), 1900));
        }
        return Optional.of(repo.save(row));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SocietyCashfreeVendor> getForBuilding(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) return Optional.empty();
        return repo.findByBuildingId(buildingId);
    }

    @Override
    @Transactional
    public Optional<SocietyCashfreeVendor> reRegister(String buildingId) {
        repo.findByBuildingId(buildingId).ifPresent(r -> {
            r.setAttemptCount(0);
            r.setFailureReason(null);
            // Force a pending state so tryRegisterIfReady doesn't
            // short-circuit on ACTIVE/IN_BANK_VALIDATION. The bank
            // sync path in tryRegisterIfReady will then re-issue a
            // PATCH with the current bank details.
            r.setStatus(CashfreeVendorStatus.PENDING_BANK);
            repo.save(r);
        });
        return tryRegisterIfReadyForSociety(buildingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocietyCashfreeVendor> listAll() {
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

    private PropertyClient.SocietyBankDetails safeFetchBank(String buildingId) {
        try {
            return propertyClient.getSocietyBankDetails(buildingId);
        } catch (Exception ex) {
            log.debug("getSocietyBankDetails({}) failed: {}", buildingId, ex.getMessage());
            return null;
        }
    }

    private static CashfreeVendorStatus mapCashfreeStatus(String status) {
        if (status == null) return CashfreeVendorStatus.REGISTERING;
        return switch (status.toUpperCase()) {
            case "ACTIVE"             -> CashfreeVendorStatus.ACTIVE;
            case "IN_BANK_VALIDATION" -> CashfreeVendorStatus.IN_BANK_VALIDATION;
            case "REJECTED", "BLOCKED", "DELETED" -> CashfreeVendorStatus.REJECTED;
            default                   -> CashfreeVendorStatus.REGISTERING;
        };
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return "Society";
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
