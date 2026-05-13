package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentApprovedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentRejectedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentUploadedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.DocumentServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for Document events. Topic resolved from
 * {@link KafkaTopicProperties#getDocumentTopic()}; key is documentId.
 */
@Service
@Slf4j
public class DocumentEventImpl implements DocumentServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public DocumentEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                             KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendDocumentUploaded(DocumentUploadedEvent event) {
        log.info("→ {} : document.uploaded documentId={} type={}",
                topics.getDocumentTopic(), event.getDocumentId(), event.getDocumentType());
        kafkaTemplate.send(topics.getDocumentTopic(), event.getDocumentId(), event);
    }

    @Override
    public void sendDocumentVerified(DocumentVerifiedEvent event) {
        log.info("→ {} : document.verified documentId={} fraud={}",
                topics.getDocumentTopic(), event.getDocumentId(), event.getFraudFlag());
        kafkaTemplate.send(topics.getDocumentTopic(), event.getDocumentId(), event);
    }

    @Override
    public void sendDocumentExtracted(DocumentExtractedEvent event) {
        log.info("→ {} : document.extracted documentId={} confidence={}",
                topics.getDocumentTopic(), event.getDocumentId(), event.getConfidenceScore());
        kafkaTemplate.send(topics.getDocumentTopic(), event.getDocumentId(), event);
    }

    @Override
    public void sendDocumentApproved(DocumentApprovedEvent event) {
        log.info("→ {} : document.approved documentId={} decidedBy={}",
                topics.getDocumentTopic(), event.getDocumentId(), event.getDecidedBy());
        kafkaTemplate.send(topics.getDocumentTopic(), event.getDocumentId(), event);
    }

    @Override
    public void sendDocumentRejected(DocumentRejectedEvent event) {
        log.info("→ {} : document.rejected documentId={} decidedBy={} reason={}",
                topics.getDocumentTopic(), event.getDocumentId(),
                event.getDecidedBy(), event.getRejectionReason());
        kafkaTemplate.send(topics.getDocumentTopic(), event.getDocumentId(), event);
    }
}
