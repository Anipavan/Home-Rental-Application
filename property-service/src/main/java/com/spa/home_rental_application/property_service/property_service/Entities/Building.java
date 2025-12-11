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
public class Building {
    @Id
    String buildingId;
    String buildingName;
    String ownerId;
    String buildingAddress;
    String buildingCity;
    String buildingState;
    String buildingTotalFloors;
    String buildingTotalFlats;
    String amenities;
    String createdDt;
    String updatedDt;

    @Override
    public String toString() {
        return "Building{" +
                "buildingId='" + buildingId + '\'' +
                ", buildingName='" + buildingName + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", buildingAddress='" + buildingAddress + '\'' +
                ", buildingCity='" + buildingCity + '\'' +
                ", buildingState='" + buildingState + '\'' +
                ", buildingTotalFloors='" + buildingTotalFloors + '\'' +
                ", buildingTotalFlats='" + buildingTotalFlats + '\'' +
                ", amenities='" + amenities + '\'' +
                ", createdDt='" + createdDt + '\'' +
                ", updatedDt='" + updatedDt + '\'' +
                '}';
    }
}
