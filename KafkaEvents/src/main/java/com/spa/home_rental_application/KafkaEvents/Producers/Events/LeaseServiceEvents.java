package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseExpiringEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseSignedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseTerminatedEvent;

/** Producer contract for Lease Service domain events. */
public interface LeaseServiceEvents {
    void sendLeaseSigned(LeaseSignedEvent event);
    void sendLeaseExpiring(LeaseExpiringEvent event);
    void sendLeaseRenewed(LeaseRenewedEvent event);
    void sendLeaseTerminated(LeaseTerminatedEvent event);
}
