package com.spa.home_rental_application.kyc_service.mapper;

import com.spa.home_rental_application.kyc_service.DTO.Response.KycReportDto;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycResponseDto;
import com.spa.home_rental_application.kyc_service.Entities.KycRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class KycMapper {

    public KycResponseDto toResponse(KycRecord r) {
        if (r == null) return null;
        return new KycResponseDto(
                r.getId(),
                r.getUserId(),
                r.getKycProvider(),
                r.getVerificationStatus(),
                r.getAadhaarVerified(),
                r.getPanVerified(),
                maskPan(r.getPanNumber()),
                r.getFaceMatchScore(),
                r.getDigilockerLinked(),
                r.getConsentRecorded(),
                r.getKycReferenceId(),
                r.getFailureReason(),
                r.getFailureCode(),
                r.getVerifiedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    public KycReportDto toReport(KycRecord r) {
        if (r == null) return null;
        return new KycReportDto(
                r.getUserId(),
                r.getKycProvider(),
                r.getVerificationStatus(),
                r.getAadhaarVerified(),
                r.getPanVerified(),
                r.getDigilockerLinked(),
                r.getConsentRecorded(),
                deriveConfidence(r),
                r.getVerifiedAt(),
                LocalDateTime.now()
        );
    }

    /** "BSEPM4567K" → "BSEP****7K". Never returns the raw value. */
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 6) return null;
        return pan.substring(0, 4) + "****" + pan.substring(pan.length() - 2);
    }

    /**
     * Confidence is HIGH when Aadhaar + PAN + DigiLocker all clear, MEDIUM when
     * Aadhaar alone clears, otherwise LOW. Used by the AI risk model.
     */
    private String deriveConfidence(KycRecord r) {
        boolean a = Boolean.TRUE.equals(r.getAadhaarVerified());
        boolean p = Boolean.TRUE.equals(r.getPanVerified());
        boolean d = Boolean.TRUE.equals(r.getDigilockerLinked());
        if (a && p && d) return "HIGH";
        if (a && p) return "MEDIUM";
        if (a || p) return "LOW";
        return "NONE";
    }
}
