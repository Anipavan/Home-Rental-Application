package com.spa.home_rental_application.compliance_service.service;

import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateReraLeaseRequest;
import com.spa.home_rental_application.compliance_service.DTO.Request.ReraRegisterRequest;
import com.spa.home_rental_application.compliance_service.DTO.Response.ReraRegistrationResponseDto;

import java.util.List;

public interface ReraService {

    ReraRegistrationResponseDto register(ReraRegisterRequest request);

    List<ReraRegistrationResponseDto> getStatusForProperty(String propertyId);

    /** Returns metadata to embed in the lease PDF that the Lease Service produces. */
    String generateReraLeaseMetadata(GenerateReraLeaseRequest request);
}
