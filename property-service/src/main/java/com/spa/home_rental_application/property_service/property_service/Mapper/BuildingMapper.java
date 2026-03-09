package com.spa.home_rental_application.property_service.property_service.Mapper;

import com.spa.home_rental_application.property_service.property_service.DTO.BuildingCreateRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.BuildingResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.BuildingUpdateRequest;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class BuildingMapper {

    public Building toEntity(BuildingCreateRequest request) {
        return Building.builder()
                .buildingName(request.getBuildingName())
                .ownerId(request.getOwnerId())
                .buildingAddress(request.getBuildingAddress())
                .buildingCity(request.getBuildingCity())
                .buildingState(request.getBuildingState())
                .buildingTotalFloors(request.getBuildingTotalFloors())
                .buildingTotalFlats(request.getBuildingTotalFlats())
                .amenities(request.getAmenities())
                .build();
    }

    public Building toEntity(String buildingId, BuildingUpdateRequest request) {
        Building building = new Building();
        building.setBuildingId(buildingId);

        if (request.getBuildingName() != null) {
            building.setBuildingName(request.getBuildingName());
        }
        if (request.getOwnerId() != null) {
            building.setOwnerId(request.getOwnerId());
        }
        if (request.getBuildingAddress() != null) {
            building.setBuildingAddress(request.getBuildingAddress());
        }
        if (request.getBuildingCity() != null) {
            building.setBuildingCity(request.getBuildingCity());
        }
        if (request.getBuildingState() != null) {
            building.setBuildingState(request.getBuildingState());
        }
        if (request.getBuildingTotalFloors() != null) {
            building.setBuildingTotalFloors(request.getBuildingTotalFloors());
        }
        if (request.getBuildingTotalFlats() != null) {
            building.setBuildingTotalFlats(request.getBuildingTotalFlats());
        }
        if (request.getAmenities() != null) {
            building.setAmenities(request.getAmenities());
        }

        return building;
    }

    public BuildingResponse toResponse(Building entity) {
        return BuildingResponse.builder()
                .buildingId(entity.getBuildingId())
                .buildingName(entity.getBuildingName())
                .ownerId(entity.getOwnerId())
                .buildingAddress(entity.getBuildingAddress())
                .buildingCity(entity.getBuildingCity())
                .buildingState(entity.getBuildingState())
                .buildingTotalFloors(entity.getBuildingTotalFloors())
                .buildingTotalFlats(entity.getBuildingTotalFlats())
                .amenities(entity.getAmenities())
                .createdDt(entity.getCreatedDt())
                .updatedDt(entity.getUpdatedDt())
                .build();
    }

    public List<BuildingResponse> toResponseList(List<Building> entities) {
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
