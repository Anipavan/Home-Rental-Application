package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.BuildingHasFlatsException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.impl.BuildingImpul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @Mock BuildingRepo buildingRepo;
    @Mock FlatRepo flatRepo;
    @Mock PropertyServiceEvents eventProducer;

    @InjectMocks BuildingImpul service;

    @BeforeEach
    void resetMocks() {
        reset(buildingRepo, flatRepo, eventProducer);
    }

    @Test
    void createBuilding_persists_andPublishesPropertyCreated() {
        BuildingRequestDTO req = new BuildingRequestDTO("Riviera Heights", "OWN-1",
                "1 Brigade Rd", "Bengaluru", "KA", 5, 20, "Pool, Gym");
        when(buildingRepo.save(any(Building.class))).thenAnswer(inv -> inv.getArgument(0));

        BuildingResponseDTO resp = service.createBuilding(req);

        assertThat(resp).isNotNull();
        assertThat(resp.buildingName()).isEqualTo("Riviera Heights");

        ArgumentCaptor<PropertyCreatedEvent> evt = ArgumentCaptor.forClass(PropertyCreatedEvent.class);
        verify(eventProducer).sendPropertyCreated(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("property.created");
        assertThat(evt.getValue().getOwnerId()).isEqualTo("OWN-1");
    }

    @Test
    void deleteBuilding_throwsWhenActiveFlatsExist() {
        Building b = Building.builder().buildingId("BLD-1").ownerId("OWN-1").build();
        when(buildingRepo.findById("BLD-1")).thenReturn(Optional.of(b));
        // one active flat returned
        var flat = new com.spa.home_rental_application.property_service.property_service.Entities.Flat();
        flat.setIsDeleted(false);
        when(flatRepo.findByBuildingId("BLD-1")).thenReturn(List.of(flat));

        assertThatThrownBy(() -> service.deleteBuildingById("BLD-1"))
                .isInstanceOf(BuildingHasFlatsException.class)
                .hasMessageContaining("active flat");

        verify(buildingRepo, never()).save(any());
    }

    @Test
    void getBuildingById_throwsRecordNotFoundWhenAbsent() {
        when(buildingRepo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBuildingById("missing"))
                .isInstanceOf(RecordNotFoundException.class);
    }
}
