package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewModeratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewSubmittedEvent;

/** Producer contract for Review Service domain events. */
public interface ReviewServiceEvents {
    void sendReviewSubmitted(ReviewSubmittedEvent event);
    void sendReviewModerated(ReviewModeratedEvent event);
}
