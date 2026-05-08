package com.spa.home_rental_application.lease_service.consumer;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.lease_service.DTO.Request.TerminateLeaseRequest;
import com.spa.home_rental_application.lease_service.Entities.Lease;
import com.spa.home_rental_application.lease_service.repository.LeaseRepository;
import com.spa.home_rental_application.lease_service.service.LeaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Listens to {@code property-events.flat.vacated}. When a flat is vacated,
 * any ACTIVE lease tied to that flat is auto-terminated with reason EXPIRY.
 */
@Component
@Slf4j
public class FlatVacatedConsumer {

    private final LeaseRepository leaseRepository;
    private final LeaseService leaseService;

    public FlatVacatedConsumer(LeaseRepository leaseRepository, LeaseService leaseService) {
        this.leaseRepository = leaseRepository;
        this.leaseService = leaseService;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "hra-lease-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onFlatVacated(FlatVacatedEvent event) {
        if (event == null || event.getFlatId() == null) {
            log.debug("Ignoring property-event with null flatId");
            return;
        }
        if (event.getEventType() != null
                && !"flat.vacated".equalsIgnoreCase(event.getEventType())) {
            log.debug("Skipping property-event type={}", event.getEventType());
            return;
        }
        try {
            for (Lease lease : leaseRepository.findByFlatId(event.getFlatId())) {
                if ("ACTIVE".equals(lease.getStatus()) || "DRAFT".equals(lease.getStatus())) {
                    leaseService.terminate(lease.getId(), new TerminateLeaseRequest(
                            "EXPIRY",
                            tryParseDate(event.getEndDate()),
                            "Auto-terminated on flat.vacated event"));
                    log.info("Auto-terminated leaseId={} on flat.vacated flatId={}",
                            lease.getId(), event.getFlatId());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to auto-terminate leases for flatId={}", event.getFlatId(), ex);
        }
    }

    private LocalDate tryParseDate(String iso) {
        try {
            return iso == null ? LocalDate.now() : LocalDate.parse(iso);
        } catch (Exception ex) {
            return LocalDate.now();
        }
    }
}
