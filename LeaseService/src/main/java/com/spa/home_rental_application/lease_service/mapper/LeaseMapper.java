package com.spa.home_rental_application.lease_service.mapper;

import com.spa.home_rental_application.lease_service.DTO.Response.LeaseHistoryDto;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseResponseDto;
import com.spa.home_rental_application.lease_service.Entities.Lease;
import com.spa.home_rental_application.lease_service.Entities.LeaseHistory;
import org.springframework.stereotype.Component;

@Component
public class LeaseMapper {

    public LeaseResponseDto toResponse(Lease l) {
        if (l == null) return null;
        return new LeaseResponseDto(
                l.getId(),
                l.getTenantId(),
                l.getFlatId(),
                l.getOwnerId(),
                l.getLeaseNumber(),
                l.getStartDate(),
                l.getEndDate(),
                l.getRentAmount(),
                l.getSecurityDeposit(),
                l.getRentIncrementPercent(),
                l.getStatus(),
                l.getReraAgreementNumber(),
                l.getDocumentUrl(),
                l.getDigitalSignatureStatus(),
                l.getAiRenewalProbability(),
                l.getExpiryWarningSentAt(),
                l.getTerminatedAt(),
                l.getTerminationReason(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    public LeaseHistoryDto toHistoryDto(LeaseHistory h) {
        if (h == null) return null;
        return new LeaseHistoryDto(
                h.getId(),
                h.getLeaseId(),
                h.getEventType(),
                h.getPreviousRent(),
                h.getNewRent(),
                h.getChangedBy(),
                h.getNotes(),
                h.getChangedAt()
        );
    }
}
