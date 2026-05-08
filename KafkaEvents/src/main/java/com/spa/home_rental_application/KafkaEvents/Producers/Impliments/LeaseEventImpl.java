package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseExpiringEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseSignedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseTerminatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.LeaseServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for Lease events. Topic resolved from
 * {@link KafkaTopicProperties#getLeaseTopic()}; key is leaseId so all events
 * for the same lease land on the same partition (in-order delivery).
 */
@Service
@Slf4j
public class LeaseEventImpl implements LeaseServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public LeaseEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                          KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendLeaseSigned(LeaseSignedEvent event) {
        log.info("→ {} : lease.signed leaseId={} tenantId={}",
                topics.getLeaseTopic(), event.getLeaseId(), event.getTenantId());
        kafkaTemplate.send(topics.getLeaseTopic(), event.getLeaseId(), event);
    }

    @Override
    public void sendLeaseExpiring(LeaseExpiringEvent event) {
        log.info("→ {} : lease.expiring leaseId={} daysLeft={}",
                topics.getLeaseTopic(), event.getLeaseId(), event.getDaysUntilExpiry());
        kafkaTemplate.send(topics.getLeaseTopic(), event.getLeaseId(), event);
    }

    @Override
    public void sendLeaseRenewed(LeaseRenewedEvent event) {
        log.info("→ {} : lease.renewed leaseId={} newEnd={}",
                topics.getLeaseTopic(), event.getLeaseId(), event.getNewEndDate());
        kafkaTemplate.send(topics.getLeaseTopic(), event.getLeaseId(), event);
    }

    @Override
    public void sendLeaseTerminated(LeaseTerminatedEvent event) {
        log.info("→ {} : lease.terminated leaseId={} reason={}",
                topics.getLeaseTopic(), event.getLeaseId(), event.getTerminationReason());
        kafkaTemplate.send(topics.getLeaseTopic(), event.getLeaseId(), event);
    }
}
