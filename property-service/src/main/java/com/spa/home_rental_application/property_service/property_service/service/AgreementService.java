package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.AgreementResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;

import java.io.IOException;
import java.util.List;

public interface AgreementService {
    /** Auto-create a PENDING_SIGNATURE agreement when a flat is assigned. */
    AgreementResponseDTO createForAssignment(Flat flat);

    AgreementResponseDTO getById(String agreementId);
    List<AgreementResponseDTO> getForTenant(String tenantId);
    List<AgreementResponseDTO> getForOwner(String ownerId);
    List<AgreementResponseDTO> getForFlat(String flatId);

    AgreementResponseDTO sign(String agreementId, String signatureBase64);
    AgreementResponseDTO reject(String agreementId, String reason);

    /** Load the rendered PDF bytes for download. */
    byte[] loadDocument(String agreementId) throws IOException;
}
