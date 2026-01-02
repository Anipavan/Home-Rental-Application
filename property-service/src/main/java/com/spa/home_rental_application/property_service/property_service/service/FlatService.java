package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import org.springframework.stereotype.Service;

import java.util.List;

public interface FlatService {
    List<Flat>getAllFlats();
    Flat getflatById(String flatId);
    Flat createFlat(Flat flat);
    String deleteFlatById(String flatId);
    List<Flat>getflatsByBuildingId(String buildId);
    List<Flat>getAllVacentFlats();
    String makeFlatVacate(String flatId);
    Flat updateFlat(String flatId,Flat flat);
    Flat assignFlat(String flstId);
}
