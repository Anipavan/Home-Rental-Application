package com.spa.home_rental_application.lease_service.scheduler;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseExpiringEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.LeaseServiceEvents;
import com.spa.home_rental_application.lease_service.Entities.Lease;
import com.spa.home_rental_application.lease_service.config.LeaseProperties;
import com.spa.home_rental_application.lease_service.repository.LeaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily sweep at the configured cron (defaults to 02:00). Two responsibilities:
 *
 * <ol>
 *   <li>For ACTIVE leases ending within {@code app.lease.expiry-warning-days},
 *       emit a {@code lease.expiring} event and stamp {@code expiry_warning_sent_at}
 *       so we never re-send the same warning.</li>
 *   <li>For ACTIVE leases whose {@code end_date} has already passed, flip status
 *       to EXPIRED.</li>
 * </ol>
 */
@Component
@Slf4j
public class LeaseExpiryScheduler {

    private final LeaseRepository leaseRepository;
    private final LeaseServiceEvents events;
    private final LeaseProperties props;

    public LeaseExpiryScheduler(LeaseRepository leaseRepository,
                                LeaseServiceEvents events,
                                LeaseProperties props) {
        this.leaseRepository = leaseRepository;
        this.events = events;
        this.props = props;
    }

    @Scheduled(cron = "${app.lease.expiry-cron:0 0 2 * * *}")
    @Transactional
    public void sweep() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(props.getExpiryWarningDays());
        log.info("LeaseExpiryScheduler running today={} threshold={}", today, threshold);

        List<Lease> expiring = leaseRepository.findExpiringWithoutWarning(today, threshold);
        log.info("Found {} leases needing expiry warnings", expiring.size());
        for (Lease lease : expiring) {
            int daysLeft = (int) java.time.temporal.ChronoUnit.DAYS.between(today, lease.getEndDate());
            events.sendLeaseExpiring(LeaseExpiringEvent.builder()
                    .eventType("lease.expiring")
                    .leaseId(lease.getId())
                    .tenantId(lease.getTenantId())
                    .flatId(lease.getFlatId())
                    .ownerId(lease.getOwnerId())
                    .endDate(lease.getEndDate())
                    .daysUntilExpiry(daysLeft)
                    .rentAmount(lease.getRentAmount())
                    .timestamp(LocalDateTime.now())
                    .build());
            lease.setExpiryWarningSentAt(LocalDateTime.now());
            leaseRepository.save(lease);
        }

        List<Lease> overdue = leaseRepository.findActivePastEndDate(today);
        log.info("Found {} leases past end date — flipping to EXPIRED", overdue.size());
        for (Lease lease : overdue) {
            lease.setStatus("EXPIRED");
            leaseRepository.save(lease);
        }
    }
}
