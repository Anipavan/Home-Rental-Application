package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.CityResponseDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.StateResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.RefCity;
import com.spa.home_rental_application.property_service.property_service.Entities.RefState;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.RefCityRepo;
import com.spa.home_rental_application.property_service.property_service.repository.RefStateRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only API for the reference dropdown data. Cached lightly via the
 * 1st-level cache + the immutable nature of the data (it doesn't change at
 * runtime). For higher traffic later, sprinkle Spring's @Cacheable on top.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final RefStateRepo stateRepo;
    private final RefCityRepo cityRepo;

    public ReferenceDataService(RefStateRepo stateRepo, RefCityRepo cityRepo) {
        this.stateRepo = stateRepo;
        this.cityRepo = cityRepo;
    }

    public List<StateResponseDTO> listStates() {
        return stateRepo.findAllByOrderByNameAsc().stream()
                .map(s -> new StateResponseDTO(s.getId(), s.getCode(), s.getName()))
                .toList();
    }

    public List<CityResponseDTO> citiesForState(Long stateId) {
        RefState state = stateRepo.findById(stateId)
                .orElseThrow(() -> new RecordNotFoundException("State " + stateId + " not found"));
        return cityRepo.findByStateIdOrderByNameAsc(stateId).stream()
                .map(c -> new CityResponseDTO(c.getId(), c.getStateId(),
                        state.getName(), c.getName(), c.getTier()))
                .toList();
    }

    /**
     * Free-text city auto-suggest (max 25 results). Returns city + the state
     * it belongs to so the dropdown can display "Bengaluru, Karnataka".
     */
    public List<CityResponseDTO> searchCities(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        List<RefCity> cities = cityRepo.searchByName(q.trim(), PageRequest.of(0, 25));
        if (cities.isEmpty()) return List.of();

        // One-shot fetch of the matching states so we can attach state names.
        Map<Long, String> stateNames = new HashMap<>();
        for (RefState s : stateRepo.findAllById(
                cities.stream().map(RefCity::getStateId).distinct().toList())) {
            stateNames.put(s.getId(), s.getName());
        }
        return cities.stream()
                .map(c -> new CityResponseDTO(c.getId(), c.getStateId(),
                        stateNames.getOrDefault(c.getStateId(), ""),
                        c.getName(), c.getTier()))
                .toList();
    }
}
