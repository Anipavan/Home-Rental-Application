package com.spa.home_rental_application.kyc_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycPanVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.KycServiceEvents;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigiLockerAuthorizeRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigiLockerCallbackRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigilockerLinkRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigioWebhookPayload;
import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.VerifyPanRequest;
import com.spa.home_rental_application.kyc_service.DTO.Response.DigiLockerAuthorizeResponse;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycReportDto;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycResponseDto;
import com.spa.home_rental_application.kyc_service.Entities.KycRecord;
import com.spa.home_rental_application.kyc_service.Exceptionclass.InvalidKycDataException;
import com.spa.home_rental_application.kyc_service.Exceptionclass.KycAlreadyVerifiedException;
import com.spa.home_rental_application.kyc_service.Exceptionclass.KycNotFoundException;
import com.spa.home_rental_application.kyc_service.Exceptionclass.KycProviderException;
import com.spa.home_rental_application.kyc_service.config.KycProperties;
import com.spa.home_rental_application.kyc_service.mapper.KycMapper;
import com.spa.home_rental_application.kyc_service.provider.DigiLockerOAuthClient;
import com.spa.home_rental_application.kyc_service.provider.EAadhaarXmlParser;
import com.spa.home_rental_application.kyc_service.provider.KycProvider;
import com.spa.home_rental_application.kyc_service.repository.KycRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@Slf4j
public class KycServiceImpl implements KycService {

    private final KycRepository kycRepository;
    private final KycMapper kycMapper;
    private final KycProvider provider;
    private final KycProperties props;
    private final KycServiceEvents events;

    /**
     * Optional DigiLocker collaborators — only wired in when
     * {@code app.kyc.provider=DIGILOCKER}. Using {@code @Autowired(required=false)}
     * keeps the service constructable on MOCK / DIGIO without dragging the
     * DigiLocker beans into the context. Both methods that touch them
     * fast-fail with a clear message if they're missing.
     */
    private final DigiLockerOAuthClient digilockerClient;
    private final EAadhaarXmlParser eAadhaarParser;

    public KycServiceImpl(KycRepository kycRepository,
                          KycMapper kycMapper,
                          KycProvider provider,
                          KycProperties props,
                          KycServiceEvents events,
                          @Autowired(required = false) DigiLockerOAuthClient digilockerClient,
                          @Autowired(required = false) EAadhaarXmlParser eAadhaarParser) {
        this.kycRepository = kycRepository;
        this.kycMapper = kycMapper;
        this.provider = provider;
        this.props = props;
        this.events = events;
        this.digilockerClient = digilockerClient;
        this.eAadhaarParser = eAadhaarParser;
    }

    // ---------- Public API ----------

    @Override
    @Transactional
    public KycResponseDto initiateKyc(String userId, InitiateKycRequest request) {
        log.info("initiateKyc userId={} provider={}", userId, provider.name());

        if (request.consentText() == null || request.consentText().isBlank()) {
            throw new InvalidKycDataException(
                    "Explicit consent text is required (DPDP Act 2023)", "CONSENT_MISSING");
        }

        KycRecord record = kycRepository.findByUserId(userId).orElseGet(() ->
                KycRecord.builder().userId(userId).build());

        if ("VERIFIED".equals(record.getVerificationStatus())) {
            throw new KycAlreadyVerifiedException(
                    "KYC already verified for userId=" + userId);
        }

        KycProvider.InitiateResult result = provider.initiate(userId, request);

        record.setKycProvider(provider.name());
        record.setAadhaarNumberHash(hashAadhaar(request.aadhaarNumber()));
        record.setPanNumber(request.panNumber());
        record.setConsentRecorded(true);
        record.setKycReferenceId(result.referenceId());
        record.setFailureCode(null);
        record.setFailureReason(null);
        if (Boolean.TRUE.equals(request.linkDigilocker())) {
            record.setDigilockerLinked(false); // pending — set true on callback
        }

        // Honour the provider's returned status. Most providers return
        // PENDING and finalize asynchronously via webhook; mocks (and
        // some sandbox modes) can return a terminal state directly. This
        // lets us short-circuit the webhook round-trip in dev.
        String providerStatus = result.providerStatus() == null
                ? "PENDING"
                : result.providerStatus();
        boolean autoVerified = false;
        if ("VERIFIED".equalsIgnoreCase(providerStatus)) {
            record.setVerificationStatus("VERIFIED");
            record.setAadhaarVerified(true);
            if (request.panNumber() != null && !request.panNumber().isBlank()) {
                record.setPanVerified(true);
            }
            record.setVerifiedAt(LocalDateTime.now());
            autoVerified = true;
        } else if ("FAILED".equalsIgnoreCase(providerStatus)) {
            record.setVerificationStatus("FAILED");
        } else {
            record.setVerificationStatus("INITIATED");
        }

        KycRecord saved = kycRepository.save(record);
        log.info("KYC initiated id={} ref={} status={}",
                saved.getId(), saved.getKycReferenceId(), saved.getVerificationStatus());

        // Fire the verified event immediately when the provider auto-verified
        // so downstream consumers (User Service KYC badge, Compliance) hear
        // about it the same way they would after a webhook.
        if (autoVerified) {
            try {
                publishVerified(saved);
            } catch (Exception ex) {
                // Event publishing is best-effort; the record is the source of truth.
                log.warn("Failed to publish kyc.verified for userId={}: {}",
                        saved.getUserId(), ex.getMessage());
            }
        }

        return kycMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public KycResponseDto getKycStatus(String userId) {
        return kycRepository.findByUserId(userId)
                .map(kycMapper::toResponse)
                .orElseThrow(() -> new KycNotFoundException(
                        "No KYC record for userId=" + userId));
    }

    @Override
    @Transactional
    public KycResponseDto verifyPan(VerifyPanRequest request) {
        log.info("verifyPan userId={} provider={} panOnly={}",
                request.userId(), provider.name(), props.isPanOnlyKyc());
        KycRecord record = kycRepository.findByUserId(request.userId())
                .orElseGet(() -> KycRecord.builder().userId(request.userId()).build());

        if ("VERIFIED".equals(record.getVerificationStatus())) {
            // Already done — return the existing record without burning a paid PAN call.
            log.info("verifyPan short-circuit — userId={} already VERIFIED", request.userId());
            return kycMapper.toResponse(record);
        }

        KycProvider.PanResult res = provider.verifyPan(
                request.panNumber(),
                request.panHolderName(),
                request.dateOfBirth());
        record.setPanNumber(request.panNumber());
        record.setPanHolderName(res.panHolderName());
        record.setPanVerified(res.valid());
        record.setKycProvider(provider.name());
        record.setConsentRecorded(true);     // request-time consent is implicit in calling verify-pan
        if (record.getVerificationStatus() == null) {
            record.setVerificationStatus("INITIATED");
        }
        if (!res.valid()) {
            // SandboxKycProvider returns "VENDOR_UNAVAILABLE: ..." as the
            // failureReason for billing/quota alerts so the frontend can
            // pop a "contact admin" dialog instead of an inline error
            // banner. Split off the marker prefix here so we set a clean
            // structured failureCode + a human-readable failureReason.
            String rawReason = res.failureReason() == null ? "" : res.failureReason();
            if (rawReason.startsWith("VENDOR_UNAVAILABLE:")) {
                record.setFailureCode("VENDOR_UNAVAILABLE");
                record.setFailureReason(
                        rawReason.substring("VENDOR_UNAVAILABLE:".length()).trim());
            } else {
                record.setFailureCode("PAN_INVALID");
                record.setFailureReason(rawReason);
            }
        }

        // PAN-only KYC mode (Sandbox.co.in / Mock): a valid PAN is the WHOLE
        // KYC. Flip the record to VERIFIED and publish kyc.verified so the
        // user-service flips the verified badge. When full Aadhaar+PAN KYC
        // is wired (post-incorporation DigiLocker), set app.kyc.pan-only-kyc=false
        // and verifyPan will revert to "just sets panVerified=true" without
        // touching the overall status.
        boolean panOnlyTerminal = res.valid() && props.isPanOnlyKyc();
        if (panOnlyTerminal) {
            record.setVerificationStatus("VERIFIED");
            record.setVerifiedAt(LocalDateTime.now());
            record.setFailureCode(null);
            record.setFailureReason(null);
        }

        KycRecord saved = kycRepository.save(record);

        if (res.valid()) {
            // kyc.pan.verified is published on every successful PAN check
            // (existing behaviour — kept so PAN-only analytics still work).
            events.sendKycPanVerified(KycPanVerifiedEvent.builder()
                    .eventType("kyc.pan.verified")
                    .userId(saved.getUserId())
                    .panNumber(saved.getPanNumber())
                    .panHolderName(saved.getPanHolderName())
                    .panVerified(true)
                    .verifiedAt(LocalDateTime.now())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        if (panOnlyTerminal) {
            // kyc.verified is the "the user is now KYC'd" event — user-service
            // listens for this and flips users.kyc_status to VERIFIED.
            try {
                publishVerified(saved);
            } catch (Exception ex) {
                log.warn("Failed to publish kyc.verified for userId={}: {}",
                        saved.getUserId(), ex.getMessage());
            }
        }
        return kycMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public KycResponseDto linkDigilocker(DigilockerLinkRequest request) {
        KycRecord record = kycRepository.findByUserId(request.userId())
                .orElseThrow(() -> new KycNotFoundException(
                        "Initiate KYC before linking DigiLocker for userId=" + request.userId()));

        // Real impl would call Digio's DigiLocker API with the auth code; here
        // we record the linkage and let the regular webhook flow finalise it.
        record.setDigilockerLinked(true);
        log.info("DigiLocker linked userId={}", request.userId());
        return kycMapper.toResponse(kycRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public KycReportDto getKycReport(String userId) {
        return kycRepository.findByUserId(userId)
                .map(kycMapper::toReport)
                .orElseThrow(() -> new KycNotFoundException(
                        "No KYC record for userId=" + userId));
    }

    @Override
    @Transactional
    public KycResponseDto handleDigioCallback(DigioWebhookPayload payload) {
        log.info("Digio callback ref={} userId={} status={}",
                payload.referenceId(), payload.userId(), payload.status());

        KycRecord record = resolveByReference(payload);
        if ("SUCCESS".equalsIgnoreCase(payload.status())) {
            record.setAadhaarVerified(true);
            if (payload.panNumber() != null) {
                record.setPanNumber(payload.panNumber());
                record.setPanHolderName(payload.panHolderName());
                record.setPanVerified(true);
            }
            if (Boolean.TRUE.equals(payload.digilockerLinked())) {
                record.setDigilockerLinked(true);
            }
            record.setFaceMatchScore(payload.faceMatchScore());
            record.setVerificationStatus("VERIFIED");
            record.setVerifiedAt(LocalDateTime.now());
            KycRecord saved = kycRepository.save(record);
            publishVerified(saved);
            return kycMapper.toResponse(saved);
        }

        record.setVerificationStatus("FAILED");
        record.setFailureCode(payload.failureCode());
        record.setFailureReason(payload.failureReason());
        KycRecord saved = kycRepository.save(record);
        publishFailed(saved);
        return kycMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void ensurePendingRecord(String userId) {
        if (kycRepository.existsByUserId(userId)) {
            log.debug("KYC record already exists for userId={} — skipping stub creation", userId);
            return;
        }
        KycRecord stub = KycRecord.builder()
                .userId(userId)
                .verificationStatus("PENDING")
                .kycProvider(provider.name())
                .build();
        kycRepository.save(stub);
        log.info("Created PENDING KYC stub for userId={}", userId);
    }

    // ---------- DigiLocker OAuth flow ----------

    /**
     * Generates an OAuth state token (via the configured DigiLocker
     * provider), persists it on the record with a TTL, and returns the
     * authorize URL the browser should be sent to.
     *
     * <p>The state token doubles as our kyc_reference_id so the callback
     * lookup is a single indexed query. We rotate it on every
     * /authorize call — even repeat-clicking the button creates a
     * fresh token, which simplifies the "user abandoned + came back"
     * UX without needing extra cleanup.
     */
    @Override
    @Transactional
    public DigiLockerAuthorizeResponse beginDigilockerAuthorize(String userId, DigiLockerAuthorizeRequest request) {
        requireDigilockerActive();
        if (request.consentText() == null || request.consentText().isBlank()) {
            throw new InvalidKycDataException(
                    "Explicit consent text is required (DPDP Act 2023)", "CONSENT_MISSING");
        }

        KycRecord record = kycRepository.findByUserId(userId).orElseGet(() ->
                KycRecord.builder().userId(userId).build());

        if ("VERIFIED".equals(record.getVerificationStatus())) {
            throw new KycAlreadyVerifiedException(
                    "KYC already verified for userId=" + userId);
        }

        // Delegate authorize URL construction + state minting to the provider.
        // We pass a minimal Initiate request — the provider only reads
        // consent + linkDigilocker fields on its happy path.
        InitiateKycRequest providerReq = new InitiateKycRequest(
                "000000000000",                 // placeholder, never read by DigiLockerProvider
                null,
                null,
                request.consentText(),
                Boolean.TRUE
        );
        KycProvider.InitiateResult result = provider.initiate(userId, providerReq);

        record.setUserId(userId);
        record.setKycProvider(provider.name());
        record.setConsentRecorded(true);
        record.setKycReferenceId(result.referenceId());
        record.setDigilockerState(result.referenceId());
        record.setDigilockerStateExpiresAt(
                LocalDateTime.now().plusSeconds(props.getDigilocker().getStateTtlSeconds()));
        if (record.getVerificationStatus() == null
                || "PENDING".equalsIgnoreCase(record.getVerificationStatus())) {
            record.setVerificationStatus("INITIATED");
        }
        record.setFailureCode(null);
        record.setFailureReason(null);

        KycRecord saved = kycRepository.save(record);
        log.info("DigiLocker authorize started userId={} state-prefix={}",
                userId, saved.getDigilockerState().substring(0, Math.min(8, saved.getDigilockerState().length())));

        return new DigiLockerAuthorizeResponse(
                result.redirectUrl(),
                saved.getDigilockerState(),
                saved.getKycReferenceId());
    }

    /**
     * Completes a DigiLocker flow: validates the {@code state} we issued,
     * exchanges the {@code code} for a token, fetches + parses eAadhaar
     * XML, hashes Aadhaar, and flips the record to VERIFIED.
     *
     * <p>All side-effects (DB writes + event publish) happen in a single
     * transaction so a partial failure leaves the record INITIATED — the
     * user can retry without ending up in a half-verified state.
     *
     * <p>Aadhaar number lifetime: read out of the parsed XML, immediately
     * hashed via {@link #hashAadhaar}, and the local variable is then
     * cleared. The raw 12-digit value never reaches the DB, the logs,
     * the Kafka event, or any other persistent surface.
     */
    @Override
    @Transactional
    public KycResponseDto completeDigilockerCallback(DigiLockerCallbackRequest request) {
        requireDigilockerActive();

        KycRecord record = kycRepository.findByKycReferenceId(request.state())
                .orElseThrow(() -> new InvalidKycDataException(
                        "Unknown or expired DigiLocker state", "INVALID_STATE"));

        // CSRF + replay defence. The DB-persisted state must match exactly,
        // and we reject anything past the TTL stamped at /authorize time.
        if (record.getDigilockerState() == null
                || !record.getDigilockerState().equals(request.state())) {
            log.warn("DigiLocker callback state mismatch userId={}", record.getUserId());
            throw new InvalidKycDataException("State token mismatch", "INVALID_STATE");
        }
        if (record.getDigilockerStateExpiresAt() == null
                || LocalDateTime.now().isAfter(record.getDigilockerStateExpiresAt())) {
            log.warn("DigiLocker callback state expired userId={}", record.getUserId());
            // Clear the state so a fresh /authorize call can issue a new one.
            record.setDigilockerState(null);
            record.setDigilockerStateExpiresAt(null);
            kycRepository.save(record);
            throw new InvalidKycDataException("State token expired — please try again", "EXPIRED_STATE");
        }

        // Single-use: clear the state now so a second call with the same
        // (code, state) can't replay. We do this even if the exchange
        // fails — DigiLocker invalidates the code anyway, so a retry
        // would need a fresh /authorize round-trip.
        record.setDigilockerState(null);
        record.setDigilockerStateExpiresAt(null);

        String accessToken = null;
        try {
            accessToken = digilockerClient.exchangeCodeForToken(request.code());
            String xml = digilockerClient.fetchEAadhaarXml(accessToken);
            EAadhaarXmlParser.EAadhaarData data = eAadhaarParser.parse(xml);

            // Hash Aadhaar IMMEDIATELY and discard the plain text. Only
            // the hash + last-4 ever touch the DB. (DPDP §8(4) data
            // minimisation — store the minimum we need to prove
            // verification, not the source data.)
            String aadhaarHash = hashAadhaar(data.rawUid());
            record.setAadhaarNumberHash(aadhaarHash);
            record.setAadhaarLast4(data.last4());
            record.setPanHolderName(data.name());
            record.setDateOfBirth(data.dob());
            record.setAadhaarVerified(true);
            record.setDigilockerLinked(true);
            record.setConsentRecorded(true);
            record.setVerificationStatus("VERIFIED");
            record.setVerifiedAt(LocalDateTime.now());
            // face_match isn't part of the DigiLocker eAadhaar flow — null is the right value.
            record.setFaceMatchScore(null);

            KycRecord saved = kycRepository.save(record);
            try {
                publishVerified(saved);
            } catch (Exception ex) {
                log.warn("Failed to publish kyc.verified for userId={}: {}",
                        saved.getUserId(), ex.getMessage());
            }
            log.info("DigiLocker KYC VERIFIED userId={} last4={}",
                    saved.getUserId(), saved.getAadhaarLast4());
            return kycMapper.toResponse(saved);
        } catch (KycProviderException e) {
            record.setVerificationStatus("FAILED");
            record.setFailureCode("DIGILOCKER_PROVIDER");
            record.setFailureReason(e.getMessage());
            KycRecord saved = kycRepository.save(record);
            try {
                publishFailed(saved);
            } catch (Exception ex) {
                log.warn("Failed to publish kyc.failed for userId={}: {}",
                        saved.getUserId(), ex.getMessage());
            }
            log.warn("DigiLocker KYC FAILED userId={} reason={}",
                    saved.getUserId(), e.getMessage());
            throw e;
        } finally {
            // Belt-and-braces: scrub the local access_token. The JVM is
            // free to keep the byte[] backing the String around, but the
            // reference is dead so the GC can reclaim it any time.
            //noinspection UnusedAssignment
            accessToken = null;
        }
    }

    // ---------- Helpers ----------

    /**
     * Hard fail-fast for the two DigiLocker methods when the service is
     * running on a different provider (MOCK / DIGIO). Returns a 400 to the
     * caller via {@link InvalidKycDataException} so the frontend can
     * surface a clear "wrong provider" message rather than a generic 500.
     */
    private void requireDigilockerActive() {
        if (!"DIGILOCKER".equalsIgnoreCase(provider.name())) {
            throw new InvalidKycDataException(
                    "DigiLocker flow is not the active KYC provider (active=" + provider.name() + ")",
                    "PROVIDER_MISMATCH");
        }
        if (digilockerClient == null || eAadhaarParser == null) {
            throw new IllegalStateException(
                    "DigiLocker beans missing despite provider=DIGILOCKER — check Spring context");
        }
    }

    private KycRecord resolveByReference(DigioWebhookPayload payload) {
        if (payload.referenceId() != null) {
            return kycRepository.findByKycReferenceId(payload.referenceId())
                    .orElseThrow(() -> new KycNotFoundException(
                            "No KYC record for reference=" + payload.referenceId()));
        }
        if (payload.userId() != null) {
            return kycRepository.findByUserId(payload.userId())
                    .orElseThrow(() -> new KycNotFoundException(
                            "No KYC record for userId=" + payload.userId()));
        }
        throw new InvalidKycDataException(
                "Webhook missing both referenceId and userId", "WEBHOOK_NO_KEY");
    }

    private void publishVerified(KycRecord r) {
        events.sendKycVerified(KycVerifiedEvent.builder()
                .eventType("kyc.verified")
                .userId(r.getUserId())
                .kycProvider(r.getKycProvider())
                .aadhaarHash(r.getAadhaarNumberHash())
                .panNumber(r.getPanNumber())
                .verified(true)
                .faceMatchScore(toDouble(r.getFaceMatchScore()))
                .kycReferenceId(r.getKycReferenceId())
                .verifiedAt(r.getVerifiedAt())
                .timestamp(LocalDateTime.now())
                .build());
    }

    private void publishFailed(KycRecord r) {
        events.sendKycFailed(KycFailedEvent.builder()
                .eventType("kyc.failed")
                .userId(r.getUserId())
                .kycProvider(r.getKycProvider())
                .failureCode(r.getFailureCode())
                .failureReason(r.getFailureReason())
                .failedAt(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build());
    }

    private Double toDouble(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    /**
     * SHA-256 hash of {salt || aadhaar}. Aadhaar is never persisted in plain
     * text — required by DPDP Act 2023.
     */
    private String hashAadhaar(String aadhaar) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((props.getAadhaarHashSalt() == null ? "" : props.getAadhaarHashSalt())
                    .getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(aadhaar.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }
}
