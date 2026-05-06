package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code payment-events}. Each event becomes one or more
 * notifications via the {@code NotificationService}, which renders the
 * matching template and dispatches it through the user's preferred channel(s).
 */
@Component
@Slf4j
public class PaymentEventListener {

    private final NotificationService notifications;

    public PaymentEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-payment-created",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCreatedEvent"}
    )
    public void onCreated(PaymentCreatedEvent e) {
        if (e == null || !"payment.created".equals(e.getEventType())) return;
        log.info("Received {} for paymentId={}", e.getEventType(), e.getPaymentId());
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.PAYMENT_CREATED,
                Map.of("invoiceNumber", safe(e.getInvoiceNumber()),
                        "amount",       safe(e.getAmount()),
                        "dueDate",      safe(e.getDueDate())));
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-payment-completed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCompletedEvent"}
    )
    public void onCompleted(PaymentCompletedEvent e) {
        if (e == null || !"payment.completed".equals(e.getEventType())) return;
        log.info("Received {} for paymentId={}", e.getEventType(), e.getPaymentId());
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.PAYMENT_RECEIPT,
                Map.of("amount",        safe(e.getAmount()),
                        "method",       safe(e.getPaymentMethod()),
                        "transactionId",safe(e.getTransactionId()),
                        "paidDate",     safe(e.getPaidDate())));
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-payment-overdue",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentOverdueEvent"}
    )
    public void onOverdue(PaymentOverdueEvent e) {
        if (e == null || !"payment.overdue".equals(e.getEventType())) return;
        log.info("Received {} for paymentId={}", e.getEventType(), e.getPaymentId());
        // Send on BOTH email and SMS — overdue is high-priority
        Map<String, Object> vars = Map.of(
                "amount",      safe(e.getAmount()),
                "lateFee",     safe(e.getLateFee()),
                "daysOverdue", safe(e.getDaysOverdue()));
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.PAYMENT_OVERDUE, vars);
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.SMS,
                NotificationCategory.PAYMENT_OVERDUE, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-payment-reminder",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentReminderEvent"}
    )
    public void onReminder(PaymentReminderEvent e) {
        if (e == null || !"payment.reminder".equals(e.getEventType())) return;
        log.info("Received {} for paymentId={}", e.getEventType(), e.getPaymentId());
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.PAYMENT_REMINDER,
                Map.of("daysUntilDue", safe(e.getDaysUntilDue())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
