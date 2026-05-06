package com.spa.home_rental_application.property_service.property_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.ExceptionHandler.ExceptionClass;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BuildingsController.class,
        excludeAutoConfiguration = {
                org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration.class
        })
@Import(ExceptionClass.class)
class BuildingsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean BuildingService buildingService;

    @Test
    void getAllBuildings_returnsPagedDtos() throws Exception {
        BuildingResponseDTO dto = new BuildingResponseDTO("BLD-1", "Riviera", "OWN-1",
                "1 MG Rd", "Bengaluru", "KA", 5, 20, "Pool",
                LocalDateTime.now(), LocalDateTime.now());
        Page<BuildingResponseDTO> page = new PageImpl<>(List.of(dto));
        when(buildingService.getBuildings(any())).thenReturn(page);

        mvc.perform(get("/properties/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].buildingId").value("BLD-1"))
                .andExpect(jsonPath("$.content[0].buildingName").value("Riviera"));
    }

    @Test
    void createBuilding_returnsCreated() throws Exception {
        BuildingRequestDTO req = new BuildingRequestDTO("Riviera", "OWN-1",
                "1 MG Rd", "Bengaluru", "KA", 5, 20, "Pool");
        BuildingResponseDTO resp = new BuildingResponseDTO("BLD-1", "Riviera", "OWN-1",
                "1 MG Rd", "Bengaluru", "KA", 5, 20, "Pool",
                LocalDateTime.now(), LocalDateTime.now());
        when(buildingService.createBuilding(any())).thenReturn(resp);

        mvc.perform(post("/properties/buildings/create/building")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buildingId").value("BLD-1"));
    }

    @Test
    void createBuilding_validationFails_returns400() throws Exception {
        // empty body — every required field missing
        mvc.perform(post("/properties/buildings/create/building")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void getBuildingById_notFound_returns404() throws Exception {
        when(buildingService.getBuildingById(eq("missing")))
                .thenThrow(new RecordNotFoundException("No record found with id: missing"));

        mvc.perform(get("/properties/buildings/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECORD_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/properties/buildings/missing"));
    }

    @Test
    void deleteBuilding_returns200() throws Exception {
        BuildingResponseDTO resp = new BuildingResponseDTO("BLD-1", "Riviera", "OWN-1",
                "1 MG Rd", "Bengaluru", "KA", 5, 20, "Pool",
                LocalDateTime.now(), LocalDateTime.now());
        when(buildingService.deleteBuildingById("BLD-1")).thenReturn(resp);
        mvc.perform(delete("/properties/buildings/BLD-1"))
                .andExpect(status().isOk());
    }
}
