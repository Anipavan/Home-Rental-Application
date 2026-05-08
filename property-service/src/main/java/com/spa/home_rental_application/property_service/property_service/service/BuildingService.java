package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BuildingService {

      Page<BuildingResponseDTO> getBuildings(Pageable pageable);
      BuildingResponseDTO createBuilding(BuildingRequestDTO buildingRequestDTO);
      BuildingResponseDTO getBuildingById(String buildId);
      BuildingResponseDTO deleteBuildingById(String buildId);
      BuildingResponseDTO updateBuilding(String buildId,BuildingRequestDTO buildingRequestDTO);
      List<BuildingResponseDTO> getBuildingsByOwnerId(String ownerId);
      List<String> getTenantIdsByOwner(String ownerId);

      /**
       * Case-insensitive search on buildingName / buildingAddress /
       * buildingCity. Returns at most {@code limit} hits, scoped to the
       * given owner if {@code ownerId} is non-null.
       */
      List<BuildingResponseDTO> searchBuildings(String q, String ownerId, int limit);

}
