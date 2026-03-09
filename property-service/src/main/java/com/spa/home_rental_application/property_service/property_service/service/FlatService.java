package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

public interface FlatService {
    Page<FlatResponseDTO> getAllFlats(Pageable pageable);
    FlatResponseDTO getflatById(String flatId);
    FlatResponseDTO createFlat(FlatRequestDTO flatRequestDTO);
    String deleteFlatById(String flatId);
    List<FlatResponseDTO>getflatsByBuildingId(String buildId);
    List<FlatResponseDTO>getAllVacentFlats();
    FlatResponseDTO makeFlatVacate(String flatId);
    FlatResponseDTO updateFlat(String flstId,FlatRequestDTO flatRequestDTO);
}
