package com.spa.home_rental_application.maintenance_service.maintenance_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceStatusChangedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.MaintenanceServiceEvents;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.CreateRequestDto;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.StatusChangeRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Repository.MaintenanceRequestRepository;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.impul.RequestServiceImpul;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import com.spa.home_rental_application.maintenance_service.maintenance_service.exception.IllegalStatusTransitionException;
import com.spa.home_rental_application.maintenance_service.maintenance_service.exception.RecordNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock MaintenanceRequestRepository repo;
    @Mock MaintenanceServiceEvents events;

    RequestServiceImpul service() {
        return new RequestServiceImpul(repo, events, "uploads/test");
    }

    @Test
    void createRequest_setsOpen_publishesEvent() {
        CreateRequestDto dto = new CreateRequestDto("T1", "F1", "O1",
                Category.PLUMBING, "Leaky tap", "Kitchen tap drips constantly", Priority.MEDIUM);
        when(repo.save(any(MaintenanceRequest.class))).thenAnswer(inv -> {
            MaintenanceRequest e = inv.getArgument(0);
            e.setId("MR-1");
            return e;
        });

        MaintenanceRequestResponse r = service().createRequest(dto);

        assertThat(r.id()).isEqualTo("MR-1");
        assertThat(r.status()).isEqualTo(Status.OPEN);
        assertThat(r.requestNumber()).startsWith("MR-");

        ArgumentCaptor<MaintenanceCreatedEvent> evt = ArgumentCaptor.forClass(MaintenanceCreatedEvent.class);
        verify(events).sendMaintenanceCreated(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("maintenance.created");
        assertThat(evt.getValue().getCategory()).isEqualTo("PLUMBING");
    }

    @Test
    void changeStatus_rejectsIllegalTransition() {
        MaintenanceRequest r = MaintenanceRequest.builder()
                .id("MR-1").status(Status.CLOSED).history(new ArrayList<>())
                .createdAt(Instant.now()).build();
        when(repo.findById("MR-1")).thenReturn(Optional.of(r));

        assertThatThrownBy(() ->
                service().changeStatus("MR-1", new StatusChangeRequest(Status.OPEN, "user-1")))
                .isInstanceOf(IllegalStatusTransitionException.class);

        verify(events, never()).sendMaintenanceStatusChanged(any());
    }

    @Test
    void changeStatus_OPEN_to_IN_PROGRESS_publishesStatusEvent() {
        MaintenanceRequest r = MaintenanceRequest.builder()
                .id("MR-1").tenantId("T1").status(Status.OPEN)
                .createdAt(Instant.now()).history(new ArrayList<>()).build();
        when(repo.findById("MR-1")).thenReturn(Optional.of(r));
        when(repo.save(any(MaintenanceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service().changeStatus("MR-1", new StatusChangeRequest(Status.IN_PROGRESS, "tech-9"));

        ArgumentCaptor<MaintenanceStatusChangedEvent> evt = ArgumentCaptor.forClass(MaintenanceStatusChangedEvent.class);
        verify(events).sendMaintenanceStatusChanged(evt.capture());
        assertThat(evt.getValue().getOldStatus()).isEqualTo("OPEN");
        assertThat(evt.getValue().getNewStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getRequestById_throwsWhenAbsent() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getRequestById("missing"))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void onFlatVacated_closesActiveRequestsAndEmitsEvents() {
        MaintenanceRequest r1 = MaintenanceRequest.builder()
                .id("MR-1").flatId("F1").tenantId("T1").status(Status.OPEN)
                .createdAt(Instant.now()).history(new ArrayList<>()).build();
        MaintenanceRequest r2 = MaintenanceRequest.builder()
                .id("MR-2").flatId("F1").tenantId("T1").status(Status.IN_PROGRESS)
                .createdAt(Instant.now()).history(new ArrayList<>()).build();
        when(repo.findByFlatIdAndStatusIn(eq("F1"), any())).thenReturn(java.util.List.of(r1, r2));

        service().onFlatVacated("F1", "T1");

        verify(repo, times(2)).save(any(MaintenanceRequest.class));
        verify(events, times(2)).sendMaintenanceStatusChanged(any());
        assertThat(r1.getStatus()).isEqualTo(Status.CLOSED);
        assertThat(r2.getStatus()).isEqualTo(Status.CLOSED);
    }
}
