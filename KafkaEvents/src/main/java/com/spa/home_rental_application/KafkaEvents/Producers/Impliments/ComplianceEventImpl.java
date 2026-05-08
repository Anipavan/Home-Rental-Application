package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.GstInvoiceGeneratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.ReraRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.ComplianceServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for Compliance events.
 * <p>
 * Topic resolved from {@link KafkaTopicProperties#getComplianceTopic()}; key
 * is propertyId (RERA) or invoiceId (GST) so per-entity ordering is preserved.
 */
@Service
@Slf4j
public class ComplianceEventImpl implements ComplianceServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public ComplianceEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                               KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendReraRegistered(ReraRegisteredEvent event) {
        log.info("→ {} : rera.registered propertyId={} state={}",
                topics.getComplianceTopic(), event.getPropertyId(), event.getState());
        kafkaTemplate.send(topics.getComplianceTopic(), event.getPropertyId(), event);
    }

    @Override
    public void sendGstInvoiceGenerated(GstInvoiceGeneratedEvent event) {
        log.info("→ {} : gst.invoice.generated invoiceId={} amount={}",
                topics.getComplianceTopic(), event.getInvoiceId(), event.getTotalAmount());
        kafkaTemplate.send(topics.getComplianceTopic(), event.getInvoiceId(), event);
    }
}
