package com.spa.home_rental_application.property_service.property_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.FlatOccupiedException;
import com.spa.home_rental_application.property_service.property_service.ExceptionHandler.ExceptionClass;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FlatController.class,
        excludeAutoConfiguration = {
                org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration.class
        })
@Import(ExceptionClass.class)
class FlatControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FlatService flatService;

    private FlatResponseDTO sampleResponse() {
        return new FlatResponseDTO("FLT-1", "BLD-1", "F-101", 1, 2, 1, 850.0,
                new BigDecimal("8500"), false, null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createFlat_returnsCreated() throws Exception {
        FlatRequestDTO req = new FlatRequestDTO("BLD-1", "F-101", 1, 2, 1, 850.0,
                new BigDecimal("8500"), null, null, null);
        when(flatService.createFlat(any())).thenReturn(sampleResponse());

        mvc.perform(post("/properties/flats/create/flat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("FLT-1"));
    }

    @Test
    void createFlat_invalidPayload_returns400() throws Exception {
        // missing required fields
        mvc.perform(post("/properties/flats/create/flat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void assignFlat_returnsOk() throws Exception {
        AssignFlatRequest req = new AssignFlatRequest("USR-7",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));
        when(flatService.assignFlat(any(), any())).thenReturn(sampleResponse());

        mvc.perform(post("/properties/flats/FLT-1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void assignFlat_alreadyOccupied_returns409() throws Exception {
        AssignFlatRequest req = new AssignFlatRequest("USR-7",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));
        when(flatService.assignFlat(any(), any()))
                .thenThrow(new FlatOccupiedException("Flat already occupied"));

        mvc.perform(post("/properties/flats/FLT-1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FLAT_OCCUPIED"));
    }

    @Test
    void vacateFlat_returnsOk() throws Exception {
        when(flatService.makeFlatVacate("FLT-1")).thenReturn(sampleResponse());
        mvc.perform(post("/properties/flats/FLT-1/vacate"))
                .andExpect(status().isOk());
    }
}
