package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
@Service
@Slf4j
public class FlatServiceImpul implements FlatService {
    @Autowired
    FlatRepo flatRepo;

    @Override
    public List<Flat> getAllFlats() {
        return flatRepo.findAll();
    }

    @Override
    public Flat getflatById(String flatId) {
        Flat flat= flatRepo.findById(flatId).orElseThrow
                (()-> new RecordNotFoundException("Flat with the requested Id is not found."+flatId));
        return flat;
    }

    @Override
    public Flat createFlat(Flat flat) {
        if (flat.getId() == null || flat.getId().isBlank()) {
            String fid= String.valueOf(UUID.randomUUID());
            log.info("Flat Id is found null, hence setting up the id to ID: {}",fid);
            flat.setId ("FLT-" + fid);
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String now = LocalDateTime.now().format(formatter);

        if (flat.getCreatedAt() == null) {
            flat.setCreatedAt(LocalDateTime.parse(now));
        }
       flat.setUpdatedAt(LocalDateTime.parse(now));
        return flatRepo.save(flat);
    }

    @Override
    public String deleteFlatById(String flatId) {
        flatRepo.findById(flatId).orElseThrow(() -> new RecordNotFoundException("No record found with the given id: " + flatId));
        flatRepo.deleteById(flatId);
        return "Record with id " + flatId + " has been deleted successfully.";
    }

    @Override
    public List<Flat> getflatsByBuildingId(String buildId) {
        List<Flat> flats = flatRepo.findByBuildingId(buildId);
        if (flats.isEmpty()) {
            throw new RecordNotFoundException(
                    "No buildings found for building with id: " + buildId);
        }
        return flats;
    }

    @Override
    public List<Flat> getAllVacentFlats() {
        return flatRepo.findByIsOccupiedFalse();
    }

    @Override
    public String makeFlatVacate(String flatId) {
        int result=flatRepo.markFlatVacant(flatId);
        if(result==1)
            return "Flat vacent";
        return "Could not update the flat to vacent";
    }

    @Override
    public Flat updateFlat(String flstId, Flat flat) {
        return flatRepo.save(flat);
    }
}
