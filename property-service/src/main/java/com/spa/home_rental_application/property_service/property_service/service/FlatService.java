package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FlatService {
    Page<FlatResponseDTO> getAllFlats(Pageable pageable);
    FlatResponseDTO getflatById(String flatId);
    FlatResponseDTO createFlat(FlatRequestDTO flatRequestDTO);
    FlatResponseDTO deleteFlatById(String flatId);
    List<FlatResponseDTO> getflatsByBuildingId(String buildId);
    List<FlatResponseDTO> getflatsByTenantId(String tenantId);
    List<FlatResponseDTO> getAllVacentFlats();
    FlatResponseDTO makeFlatVacate(String flatId);
    FlatResponseDTO updateFlat(String flatId, FlatRequestDTO flatRequestDTO);
    FlatResponseDTO assignFlat(String flatId, AssignFlatRequest req);

    /**
     * Geosearch: every non-deleted, non-occupied flat whose parent
     * building has a geo-pin within {@code radiusKm} kilometres of
     * the given coordinates. Pin-less buildings are excluded.
     */
    List<FlatResponseDTO> findFlatsNear(double lat, double lng, double radiusKm);
}
