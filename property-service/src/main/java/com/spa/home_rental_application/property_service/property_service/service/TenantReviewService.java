package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateTenantReviewRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.TenantReviewResponseDTO;
import java.util.List;

public interface TenantReviewService {
    TenantReviewResponseDTO create(CreateTenantReviewRequest body);
    List<TenantReviewResponseDTO> forTenant(String tenantId);
    List<TenantReviewResponseDTO> forOwner(String ownerId);
    List<TenantReviewResponseDTO> forFlat(String flatId);
}
