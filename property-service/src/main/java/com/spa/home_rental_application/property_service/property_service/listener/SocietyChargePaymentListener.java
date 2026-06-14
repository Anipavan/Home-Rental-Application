package com.spa.home_rental_application.property_service.property_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCompletedEvent;
import com.spa.home_rental_application.property_service.property_service.service.SocietyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Society-charge → Razorpay completion hook. When payment-service
 * publishes a {@link PaymentCompletedEvent} (rent OR society alike),
 * this consumer asks SocietyService whether the paymentId matches any
 * society-charge rows; if it does, those rows are flipped PAID with
 * {@code paid_via=RAZORPAY} and the event's paid date.
 *
 * <p>The lookup-by-paymentId is the only way the system tells rent
 * payments apart from society payments — rent payments aren't linked
 * to maintenance_collection rows, so the service's
 * {@code findByPaymentId} returns empty and the consumer exits
 * silently. No double-work, no event-type filtering needed.
 *
 * <p>Idempotency: the consumer can be re-delivered safely. The service
 * method short-circuits when the matched rows are already PAID and
 * skips them, so Kafka commit-on-retry just re-walks an already-done
 * batch with zero side effects.
 */
@Component
@Slf4j
public class SocietyChargePaymentListener {

    private final SocietyService societyService;

    public SocietyChargePaymentListener(SocietyService societyService) {
        this.societyService = societyService;
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "hra-property-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event == null || event.getPaymentId() == null) {
            log.warn("Ignoring payment-events message with null paymentId");
            return;
        }
        // Filter to the success type — payment-service uses the same
        // topic for "completed" + future "refunded" / "failed" events.
        // If eventType is null (legacy producer), assume completed.
        if (event.getEventType() != null
                && !"payment.completed".equalsIgnoreCase(event.getEventType())) {
            return;
        }
        LocalDate paidOn = event.getPaidDate() == null
                ? LocalDate.now()
                : event.getPaidDate().atZone(ZoneId.systemDefault()).toLocalDate();
        try {
            societyService.onSocietyChargePaymentCompleted(event.getPaymentId(), paidOn);
        } catch (Exception ex) {
            // Don't let one bad event poison the group-id offset.
            // Log + swallow so the listener container keeps consuming.
            log.error("Failed to apply society-charge payment.completed for paymentId={}: {}",
                    event.getPaymentId(), ex.toString(), ex);
        }
    }
}
