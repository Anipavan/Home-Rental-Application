package com.spa.home_rental_application.property_service.property_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingCreateRequest {
    private String buildingName;
    private String ownerId;
    private String buildingAddress;
    private String buildingCity;
    private String buildingState;
    private String buildingTotalFloors;
    private String buildingTotalFlats;
    private String amenities;
}
