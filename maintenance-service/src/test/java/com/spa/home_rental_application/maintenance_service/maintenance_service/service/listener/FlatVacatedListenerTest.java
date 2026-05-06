package com.spa.home_rental_application.maintenance_service.maintenance_service.service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.listener.FlatVacatedListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlatVacatedListenerTest {

    @Mock RequestService requestService;

    @Test
    void onFlatVacated_dispatchesToService() {
        FlatVacatedListener listener = new FlatVacatedListener(requestService);
        FlatVacatedEvent e = FlatVacatedEvent.builder()
                .eventType("flat.vacated")
                .flatId("F1").tenantId("T1")
                .timestamp(Instant.now()).build();

        listener.onMessage(e);
        verify(requestService).onFlatVacated("F1", "T1");
    }

    @Test
    void otherEventTypes_areIgnored() {
        FlatVacatedListener listener = new FlatVacatedListener(requestService);
        FlatVacatedEvent unrelated = FlatVacatedEvent.builder()
                .eventType("unrelated.event")
                .flatId("F1").build();
        listener.onMessage(unrelated);
        verifyNoInteractions(requestService);
    }

    @Test
    void serviceFailure_isSwallowed_doesNotPropagate() {
        FlatVacatedListener listener = new FlatVacatedListener(requestService);
        doThrow(new RuntimeException("boom")).when(requestService).onFlatVacated(any(), any());
        FlatVacatedEvent e = FlatVacatedEvent.builder()
                .eventType("flat.vacated")
                .flatId("F1").tenantId("T1").build();
        // Must not throw — listener must keep consuming the topic
        listener.onMessage(e);
    }

    @Test
    void nullEvent_isIgnored() {
        FlatVacatedListener listener = new FlatVacatedListener(requestService);
        listener.onMessage(null);
        verifyNoInteractions(requestService);
    }
}
