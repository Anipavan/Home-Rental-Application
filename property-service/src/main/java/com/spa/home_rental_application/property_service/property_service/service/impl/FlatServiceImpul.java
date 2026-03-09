package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.PropertyEvent;
import com.spa.home_rental_application.property_service.property_service.DTO.FlatMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final PropertyEvent eventProducer;
    private final FlatMapper flatMapper;

    public FlatServiceImpul(FlatRepo flatRepo,
                            PropertyEvent eventProducer, FlatMapper flatMapper) {
        this.flatRepo = flatRepo;
        this.eventProducer = eventProducer;
        this.flatMapper=flatMapper;
    }

    @Override
    public Page<FlatResponseDTO> getAllFlats(Pageable pageable) {
        Page<Flat> allFlats=flatRepo.findAll(pageable);
        if(allFlats!=null)
            allFlats.map(flatMapper::toResponseDTO);
        throw new RecordNotFoundException("No Records available");
    }

    @Override
    public FlatResponseDTO getflatById(String flatId) {
        Flat flat= flatRepo.findById(flatId).orElseThrow(
                ()-> new RecordNotFoundException("Flat with the requested Id is not found."+flatId));
        return flatMapper.toResponseDTO(flat);
    }

    @Override
    public FlatResponseDTO createFlat(FlatRequestDTO flatRequestDTO) {
        Flat flat=flatMapper.toEntity(flatRequestDTO);
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
        return flatMapper.toResponseDTO(flatRepo.save(flat));
    }

    @Override
    public Flat deleteFlatById(String flatId) {
        Flat flat =flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("No record found with the given id: " + flatId));

        flat.setDeleted(true);


        return flatRepo.save(flat);
    }

    @Override
    public List<FlatResponseDTO> getflatsByBuildingId(String buildId) {
        List<Flat> flats = flatRepo.findByBuildingId(buildId);
        if (flats.isEmpty()) {
            throw new RecordNotFoundException(
                    "No buildings found for building with id: " + buildId);
        }
        return flats.stream().map(flat->flatMapper.toResponseDTO(flat)).collect(Collectors.toList());
    }

    @Override
    public List<FlatResponseDTO> getAllVacentFlats() {

        return flatRepo.findByIsOccupiedFalse()
                .stream().map(flat -> flatMapper.toResponseDTO(flat))
                .collect(Collectors.toList());
    }

    @Override
    public FlatResponseDTO makeFlatVacate(String flatId) {
        Flat flat = flatRepo.findById(flatId)
                .orElseThrow(() -> new RecordNotFoundException(
                        "Flat with the requested Id is not found." + flatId));

        int result = flatRepo.markFlatVacant(flatId);

        if(result!=0)
        {
            eventProducer.sendFlatVacated(
                    FlatVacatedEvent.builder()
                            .eventType("flat.vacated")
                            .flatId(flatId)
                            .tenantId(flat.getTenantId())
                            .endDate(flat.getLeaseEndDate() != null ? flat.getLeaseEndDate().toString() : null)
                            .timestamp(Instant.now())
                            .build()
            );
            return flatMapper.toResponseDTO(flat);
        }
        return flatMapper.toResponseDTO(flat);
    }

    @Override
    public FlatResponseDTO updateFlat(String flstId, FlatRequestDTO flatRequestDTO) {

        Flat flat=flatMapper.toEntity(flatRequestDTO);

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
        return flatMapper.toResponseDTO(flatRepo.save(flatFound));
    }
}
