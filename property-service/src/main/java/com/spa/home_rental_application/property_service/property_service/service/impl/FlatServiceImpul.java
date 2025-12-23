package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import com.spa.home_rental_application.property_service.property_service.utils.PropertyEventProducer;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.FlatVacatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Service
@Slf4j
public class FlatServiceImpul implements FlatService {
    private final FlatRepo flatRepo;
    private final PropertyEventProducer eventProducer;

    public FlatServiceImpul(FlatRepo flatRepo,
                            PropertyEventProducer eventProducer) {
        this.flatRepo = flatRepo;
        this.eventProducer = eventProducer;
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
        if (result == 1) {
            eventProducer.sendFlatVacated(
                    FlatVacatedEvent.builder()
                            .eventType("flat.vacated")
                            .flatId(flatId)
                            .tenantId(flat.getTenantId())
                            .endDate(flat.getLeaseEndDate() != null ? flat.getLeaseEndDate().toString() : null)
                            .timestamp(Instant.now())
                            .build()
            );
            return "Flat vacent";
        }
        return "Could not update the flat to vacent";
    }

    @Override
    public Flat updateFlat(String flstId, Flat flat) {

        Flat flatFound=flatRepo.findById(flstId).orElseThrow(
                ()->new RecordNotFoundException("Flat with given Id is not present:"+flstId));
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
}
