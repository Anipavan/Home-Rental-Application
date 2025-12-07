package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;

import java.util.List;

public interface BuildingService {

      List<Building> getBuildings();
      Building createBuilding(Building building);
}
