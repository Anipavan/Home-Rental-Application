package com.spa.home_rental_application.kyc_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycPanVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.KycServiceEvents;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigilockerLinkRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigioWebhookPayload;
import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.VerifyPanRequest;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycReportDto;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycResponseDto;
import com.spa.home_rental_application.kyc_service.Entities.KycRecord;
import com.spa.home_rental_application.kyc_service.Exceptionclass.InvalidKycDataException;
import com.spa.home_rental_application.kyc_service.Exceptionclass.KycAlreadyVerifiedException;
import com.spa.home_rental_application.kyc_service.Exceptionclass.KycNotFoundException;
import com.spa.home_rental_application.kyc_service.config.KycProperties;
import com.spa.home_rental_application.kyc_service.mapper.KycMapper;
import com.spa.home_rental_application.kyc_service.provider.KycProvider;
import com.spa.home_rental_application.kyc_service.repository.KycRepository;
import lombok.extern.slf4j.Slf4j;
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

    public KycServiceImpl(KycRepository kycRepository,
                          KycMapper kycMapper,
                          KycProvider provider,
                          KycProperties props,
                          KycServiceEvents events) {
        this.kycRepository = kycRepository;
        this.kycMapper = kycMapper;
        this.provider = provider;
        this.props = props;
        this.events = events;
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
        record.setVerificationStatus("INITIATED");
        record.setFailureCode(null);
        record.setFailureReason(null);
        if (Boolean.TRUE.equals(request.linkDigilocker())) {
            record.setDigilockerLinked(false); // pending — set true on callback
        }

        KycRecord saved = kycRepository.save(record);
        log.info("KYC initiated id={} ref={}", saved.getId(), saved.getKycReferenceId());
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
        log.info("verifyPan userId={}", request.userId());
        KycRecord record = kycRepository.findByUserId(request.userId())
                .orElseGet(() -> KycRecord.builder().userId(request.userId()).build());

        KycProvider.PanResult res = provider.verifyPan(request.panNumber(), request.panHolderName());
        record.setPanNumber(request.panNumber());
        record.setPanHolderName(res.panHolderName());
        record.setPanVerified(res.valid());
        record.setKycProvider(provider.name());
        if (record.getVerificationStatus() == null) {
            record.setVerificationStatus("INITIATED");
        }
        if (!res.valid()) {
            record.setFailureCode("PAN_INVALID");
            record.setFailureReason(res.failureReason());
        }
        KycRecord saved = kycRepository.save(record);

        if (res.valid()) {
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

    // ---------- Helpers ----------

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
