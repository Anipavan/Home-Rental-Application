package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.FlatMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.FlatOccupiedException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.InvalidLeasePeriodException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.impl.FlatServiceImpul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlatServiceTest {

    @Mock FlatRepo flatRepo;
    @Mock PropertyServiceEvents events;
    @Mock FlatMapper flatMapper;

    @InjectMocks FlatServiceImpul service;

    @BeforeEach
    void resetMocks() { reset(flatRepo, events, flatMapper); }

    @Test
    void createFlat_assignsId_andTimestamps() {
        FlatRequestDTO req = new FlatRequestDTO("BLD-1", "F-101", 1, 2, 1,
                850.0, new BigDecimal("8500"), null, null, null);
        Flat blank = Flat.builder().build();
        when(flatMapper.toEntity(req)).thenReturn(blank);
        when(flatRepo.save(any(Flat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flatMapper.toResponseDTO(any(Flat.class))).thenReturn(mock(FlatResponseDTO.class));

        service.createFlat(req);

        ArgumentCaptor<Flat> saved = ArgumentCaptor.forClass(Flat.class);
        verify(flatRepo).save(saved.capture());
        assertThat(saved.getValue().getId()).startsWith("FLT-");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void assignFlat_publishesFlatOccupied_andSetsLease() {
        Flat f = Flat.builder().id("FLT-1").buildingId("BLD-1")
                .isOccupied(false).rentAmount(new BigDecimal("8500")).build();
        when(flatRepo.findById("FLT-1")).thenReturn(Optional.of(f));
        when(flatRepo.save(any(Flat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flatMapper.toResponseDTO(any(Flat.class))).thenReturn(mock(FlatResponseDTO.class));

        AssignFlatRequest body = new AssignFlatRequest("USR-7",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));
        service.assignFlat("FLT-1", body);

        assertThat(f.getIsOccupied()).isTrue();
        assertThat(f.getTenantId()).isEqualTo("USR-7");
        ArgumentCaptor<FlatOccupiedEvent> evt = ArgumentCaptor.forClass(FlatOccupiedEvent.class);
        verify(events).sendFlatOccupied(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("flat.occupied");
        assertThat(evt.getValue().getTenantId()).isEqualTo("USR-7");
    }

    @Test
    void assignFlat_alreadyOccupied_throws() {
        Flat f = Flat.builder().id("FLT-1").isOccupied(true).tenantId("USR-X").build();
        when(flatRepo.findById("FLT-1")).thenReturn(Optional.of(f));
        AssignFlatRequest body = new AssignFlatRequest("USR-7",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));
        assertThatThrownBy(() -> service.assignFlat("FLT-1", body))
                .isInstanceOf(FlatOccupiedException.class);
        verifyNoInteractions(events);
    }

    @Test
    void assignFlat_invalidLeaseWindow_throws() {
        Flat f = Flat.builder().id("FLT-1").isOccupied(false).build();
        when(flatRepo.findById("FLT-1")).thenReturn(Optional.of(f));
        // end before start
        AssignFlatRequest body = new AssignFlatRequest("USR-7",
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> service.assignFlat("FLT-1", body))
                .isInstanceOf(InvalidLeasePeriodException.class);
    }

    @Test
    void makeFlatVacate_publishesFlatVacated() {
        Flat f = Flat.builder().id("FLT-1").isOccupied(true).tenantId("USR-7")
                .leaseEndDate(LocalDate.of(2026, 12, 31)).build();
        when(flatRepo.findById("FLT-1")).thenReturn(Optional.of(f));
        when(flatRepo.save(any(Flat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flatMapper.toResponseDTO(any(Flat.class))).thenReturn(mock(FlatResponseDTO.class));

        service.makeFlatVacate("FLT-1");

        assertThat(f.getIsOccupied()).isFalse();
        assertThat(f.getTenantId()).isNull();
        ArgumentCaptor<FlatVacatedEvent> evt = ArgumentCaptor.forClass(FlatVacatedEvent.class);
        verify(events).sendFlatVacated(evt.capture());
        assertThat(evt.getValue().getTenantId()).isEqualTo("USR-7");
    }

    @Test
    void makeFlatVacate_alreadyVacant_isNoOp() {
        Flat f = Flat.builder().id("FLT-1").isOccupied(false).build();
        when(flatRepo.findById("FLT-1")).thenReturn(Optional.of(f));
        when(flatMapper.toResponseDTO(f)).thenReturn(mock(FlatResponseDTO.class));

        service.makeFlatVacate("FLT-1");

        verify(flatRepo, never()).markFlatVacant(any());
        verifyNoInteractions(events);
    }

    @Test
    void getflatById_throwsRecordNotFound() {
        when(flatRepo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getflatById("missing"))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void getflatsByBuildingId_emptyResult_throws() {
        when(flatRepo.findByBuildingId("B1")).thenReturn(List.of());
        assertThatThrownBy(() -> service.getflatsByBuildingId("B1"))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void deleteFlatById_softDeletes() {
        Flat f = Flat.builder().id("FLT-1").isDeleted(false).build();
        when(flatRepo.findById("FLT-1")).thenReturn(Optional.of(f));
        when(flatRepo.save(any(Flat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flatMapper.toResponseDTO(any(Flat.class))).thenReturn(mock(FlatResponseDTO.class));

        service.deleteFlatById("FLT-1");

        assertThat(f.getIsDeleted()).isTrue();
        assertThat(f.getUpdatedAt()).isNotNull();
    }
}
