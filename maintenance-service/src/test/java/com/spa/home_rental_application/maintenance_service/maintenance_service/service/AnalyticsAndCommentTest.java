package com.spa.home_rental_application.maintenance_service.maintenance_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceCommentAddedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.MaintenanceServiceEvents;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.AddCommentRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.AssignTechnicianRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.CategoryStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Repository.MaintenanceRequestRepository;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.impul.RequestServiceImpul;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsAndCommentTest {

    @Mock MaintenanceRequestRepository repo;
    @Mock MaintenanceServiceEvents events;

    RequestServiceImpul service() {
        return new RequestServiceImpul(repo, events, "uploads/test");
    }

    @Test
    void addComment_publishesCommentAddedEvent() {
        MaintenanceRequest r = MaintenanceRequest.builder()
                .id("MR-1").tenantId("T1").comments(new ArrayList<>())
                .createdAt(Instant.now()).build();
        when(repo.findById("MR-1")).thenReturn(Optional.of(r));
        when(repo.save(any(MaintenanceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service().addComment("MR-1", new AddCommentRequest("USR-1", "Plumber arriving at 3pm"));

        assertThat(r.getComments()).hasSize(1);
        ArgumentCaptor<MaintenanceCommentAddedEvent> evt = ArgumentCaptor.forClass(MaintenanceCommentAddedEvent.class);
        verify(events).sendMaintenanceCommentAdded(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("maintenance.comment.added");
    }

    @Test
    void assignTechnician_autoTransitionsOpenToInProgress() {
        MaintenanceRequest r = MaintenanceRequest.builder()
                .id("MR-1").tenantId("T1").status(Status.OPEN)
                .history(new ArrayList<>()).createdAt(Instant.now()).build();
        when(repo.findById("MR-1")).thenReturn(Optional.of(r));
        when(repo.save(any(MaintenanceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service().assignTechnician("MR-1", new AssignTechnicianRequest("TECH-1"));

        assertThat(r.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(r.getAssignedTo()).isEqualTo("TECH-1");
    }

    @Test
    void getCategoryStats_returnsCountForEveryCategory() {
        for (Category c : Category.values()) {
            when(repo.countByCategory(c)).thenReturn(5L);
        }
        List<CategoryStatsResponse> stats = service().getCategoryStats();
        assertThat(stats).hasSize(Category.values().length);
        assertThat(stats).allMatch(s -> s.count() == 5L);
    }

    @Test
    void getPendingRequestCount_callsCountByStatusIn() {
        when(repo.countByStatusIn(EnumSet.of(Status.OPEN, Status.IN_PROGRESS))).thenReturn(7L);
        assertThat(service().getPendingRequestCount()).isEqualTo(7L);
    }

    @Test
    void getResolutionTimeStats_emptyResolvedList_returnsZeros() {
        when(repo.findByStatus(Status.RESOLVED)).thenReturn(List.of());
        var stats = service().getResolutionTimeStats();
        assertThat(stats.sampleSize()).isZero();
        assertThat(stats.averageMinutes()).isZero();
    }
}
