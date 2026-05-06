package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for payment lifecycle events.
 * Topic comes from {@link KafkaTopicProperties#getPaymentTopic()}.
 * Message key is {@code paymentId} so events for the same payment land
 * on the same partition (in-order delivery).
 */
@Service
@Slf4j
public class PaymentEventImpl implements PaymentServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public PaymentEventImpl(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendPaymentCreated(PaymentCreatedEvent e) {
        log.info("→ {} : payment.created paymentId={} amount={}", topics.getPaymentTopic(), e.getPaymentId(), e.getAmount());
        kafkaTemplate.send(topics.getPaymentTopic(), e.getPaymentId(), e);
    }

    @Override
    public void sendPaymentCompleted(PaymentCompletedEvent e) {
        log.info("→ {} : payment.completed paymentId={} method={}", topics.getPaymentTopic(), e.getPaymentId(), e.getPaymentMethod());
        kafkaTemplate.send(topics.getPaymentTopic(), e.getPaymentId(), e);
    }

    @Override
    public void sendPaymentFailed(PaymentFailedEvent e) {
        log.info("→ {} : payment.failed paymentId={} reason={}", topics.getPaymentTopic(), e.getPaymentId(), e.getReason());
        kafkaTemplate.send(topics.getPaymentTopic(), e.getPaymentId(), e);
    }

    @Override
    public void sendPaymentOverdue(PaymentOverdueEvent e) {
        log.info("→ {} : payment.overdue paymentId={} daysOverdue={}", topics.getPaymentTopic(), e.getPaymentId(), e.getDaysOverdue());
        kafkaTemplate.send(topics.getPaymentTopic(), e.getPaymentId(), e);
    }

    @Override
    public void sendPaymentReminder(PaymentReminderEvent e) {
        log.info("→ {} : payment.reminder paymentId={} type={}", topics.getPaymentTopic(), e.getPaymentId(), e.getReminderType());
        kafkaTemplate.send(topics.getPaymentTopic(), e.getPaymentId(), e);
    }
}
