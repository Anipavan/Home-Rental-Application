package com.spa.home_rental_application.property_service.property_service.DTO;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;

import java.time.LocalDateTime;
import java.util.UUID;

public class BuildingMapper {

    public  static Building toEntity(BuildingRequestDTO dto) {

        if (dto == null) return null;

        return Building.builder()
                .buildingId(UUID.randomUUID().toString())
                .buildingName(dto.buildingName())
                .ownerId(dto.ownerId())
                .buildingAddress(dto.buildingAddress())
                .buildingCity(dto.buildingCity())
                .buildingState(dto.buildingState())
                .buildingTotalFloors(String.valueOf(dto.buildingTotalFloors()))
                .buildingTotalFlats(String.valueOf(dto.buildingTotalFlats()))
                .amenities(dto.amenities())
                .createdDt(LocalDateTime.now().toString())
                .updatedDt(LocalDateTime.now().toString())
                .build();
    }

    public static BuildingResponseDTO toDTO(Building building) {

        if (building == null) return null;

        return new BuildingResponseDTO(
                building.getBuildingId(),
                building.getBuildingName(),
                building.getOwnerId(),
                building.getBuildingAddress(),
                building.getBuildingCity(),
                building.getBuildingState(),
                Integer.valueOf(building.getBuildingTotalFloors()),
                Integer.valueOf(building.getBuildingTotalFlats()),
                building.getAmenities(),
                LocalDateTime.parse(building.getCreatedDt()),
                LocalDateTime.parse(building.getUpdatedDt())
        );
    }
}