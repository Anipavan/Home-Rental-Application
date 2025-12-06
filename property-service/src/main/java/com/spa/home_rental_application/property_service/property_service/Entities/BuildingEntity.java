package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity(name = "registeredBuildings")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BuildingEntity {
    @Id
    String buildingId;
    String buildingName;
    String buildingAddress;
    String buildingCity;
    String buildingState;
    String buildingTotalFloors;
    String buildingTotalFlats;
    String amenities;
    String createdDt;
    String updatedDt;
}
