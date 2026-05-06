package com.spa.home_rental_application.analytics_service.analytics_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCompletedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentOverdueEvent;
import com.spa.home_rental_application.analytics_service.analytics_service.service.AggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@Slf4j
public class PaymentEventListener {

    private final AggregationService agg;

    public PaymentEventListener(AggregationService agg) {
        this.agg = agg;
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-analytics-service}-payment-completed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCompletedEvent"}
    )
    public void onCompleted(PaymentCompletedEvent e) {
        if (e == null || !"payment.completed".equals(e.getEventType())) return;
        log.info("Received {} paymentId={} amount={}", e.getEventType(), e.getPaymentId(), e.getAmount());
        LocalDate paid = e.getPaidDate() == null ? LocalDate.now()
                : LocalDate.ofInstant(e.getPaidDate(), ZoneId.systemDefault());
        // We don't get dueDate on PaymentCompletedEvent; we treat paidDate as the
        // best-effort comparison anchor for trends. The Trends row only logs
        // on-time/late when dueDate is present, so passing paid here means we
        // record this payment as on-time (acceptable for v1).
        agg.onPaymentCompleted(e.getOwnerId(), e.getAmount(), paid, paid);
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-analytics-service}-payment-overdue",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentOverdueEvent"}
    )
    public void onOverdue(PaymentOverdueEvent e) {
        if (e == null || !"payment.overdue".equals(e.getEventType())) return;
        log.info("Received {} paymentId={} amount={}", e.getEventType(), e.getPaymentId(), e.getAmount());
        // We don't carry ownerId on overdue events; book against tenant ID as a fallback bucket.
        // (In v2 the producer should include ownerId.)
        agg.onPaymentOverdue(e.getTenantId(), e.getAmount(), LocalDate.now());
    }
}
