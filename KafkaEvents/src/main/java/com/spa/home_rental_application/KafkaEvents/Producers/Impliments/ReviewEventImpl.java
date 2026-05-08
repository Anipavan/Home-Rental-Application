package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewModeratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewSubmittedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.ReviewServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReviewEventImpl implements ReviewServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public ReviewEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                           KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendReviewSubmitted(ReviewSubmittedEvent event) {
        log.info("→ {} : review.submitted reviewId={} target={}/{}",
                topics.getReviewTopic(), event.getReviewId(),
                event.getTargetType(), event.getTargetId());
        kafkaTemplate.send(topics.getReviewTopic(), event.getReviewId(), event);
    }

    @Override
    public void sendReviewModerated(ReviewModeratedEvent event) {
        log.info("→ {} : review.moderated reviewId={} decision={}",
                topics.getReviewTopic(), event.getReviewId(), event.getModerationDecision());
        kafkaTemplate.send(topics.getReviewTopic(), event.getReviewId(), event);
    }
}
