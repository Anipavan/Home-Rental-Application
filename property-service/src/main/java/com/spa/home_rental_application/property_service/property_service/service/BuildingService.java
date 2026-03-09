package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;

import java.util.List;

public interface BuildingService {

      List<BuildingResponseDTO> getBuildings();
      BuildingResponseDTO createBuilding(BuildingRequestDTO buildingRequestDTO);
      BuildingResponseDTO getBuildingById(String buildId);
      BuildingResponseDTO deleteBuildingById(String buildId);
      BuildingResponseDTO updateBuilding(String buildId,BuildingRequestDTO buildingRequestDTO);
      List<BuildingResponseDTO> getBuildingsByOwnerId(String ownerId);

}
