package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FlatServiceImpul implements FlatService {
    private final FlatRepo flatRepo;

    public FlatServiceImpul(FlatRepo flatRepo){
        this.flatRepo = flatRepo;
    }

    @Override
    public List<Flat> getAllFlats() {
        List<Flat> allFlats=flatRepo.findAll();
        if(allFlats!=null)
            return allFlats;
        throw new RecordNotFoundException("No Records available");
    }

    @Override
    public Flat getflatById(String flatId) {
        Flat flat= flatRepo.findById(flatId).orElseThrow(
                ()-> new RecordNotFoundException("Flat with the requested Id is not found."+flatId));
        return flat;
    }

    @Override
    public Flat createFlat(Flat flat) {
        if (flat.getId() == null || flat.getId().isBlank()) {
            String fid= String.valueOf(UUID.randomUUID());
            log.info("Flat Id is found null, hence setting up the id to ID: {}",fid);
            flat.setId ("FLT-" + fid);
        }
        LocalDateTime now = LocalDateTime.now();

        if (flat.getCreatedAt() == null) {
            flat.setCreatedAt(now);
        }
       flat.setUpdatedAt(now);
        return flatRepo.save(flat);
    }

    @Override
    public String deleteFlatById(String flatId) {
        flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("No record found with the given id: " + flatId));
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
        Flat flat = flatRepo.findById(flatId)
                .orElseThrow(() -> new RecordNotFoundException(
                        "Flat with the requested Id is not found." + flatId));

        int result = flatRepo.markFlatVacant(flatId);
        return "Could not update the flat to vacent";
    }

    @Override
    public Flat updateFlat(String flatId, Flat flat) {

        Flat flatFound=flatRepo.findById(flatId).orElseThrow(
                ()->new RecordNotFoundException("Flat with given Id is not present:"+flatId));
        flatFound.setBuildingId(flat.getBuildingId());
        flatFound.setFlatNumber(flat.getFlatNumber());
        flatFound.setBathrooms(flat.getBathrooms());
        flatFound.setAreaSqft(flat.getAreaSqft());
        flatFound.setBedrooms(flat.getBedrooms());
        flatFound.setFloor(flat.getFloor());
        flatFound.setIsOccupied(flat.getIsOccupied());
        flatFound.setLeaseEndDate(flat.getLeaseEndDate());
        flatFound.setLeaseStartDate(flat.getLeaseStartDate());
        flatFound.setRentAmount(flat.getRentAmount());
        flatFound.setTenantId(flat.getTenantId());
        flatFound.setUpdatedAt(LocalDateTime.now());
        return flatRepo.save(flatFound);
    }

    @Override
    public Flat assignFlat(String userId) {
        Flat vacantFlat = getAllVacentFlats().stream()
                .findFirst()
                .orElse(null);

        if (vacantFlat == null) {
            return null;
        }

        vacantFlat.setTenantId(userId);
        return flatRepo.save(vacantFlat);
    }

}
