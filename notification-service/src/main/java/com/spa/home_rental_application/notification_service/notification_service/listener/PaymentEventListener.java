package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code payment-events}. Each event fans across the
 * user's reachable channels via {@link NotificationService#fanOut} —
 * the user hears about money on every channel they've configured
 * (in-app bell + email + SMS + WhatsApp), with opt-outs and
 * missing-recipient handling delegated to the service.
 *
 * <p>Earlier code used {@code sendFromTemplate(EMAIL, ...)} which
 * limited every payment-event to email-only — fine when email was
 * the only configured channel, wrong now that we want SMS + WhatsApp
 * coverage. fanOut is the canonical "tell the user on every channel"
 * helper; it writes one INAPP bell entry and one per-channel attempt,
 * so we never duplicate the bell while picking up SMS/WhatsApp legs
 * for free.
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
        notifications.fanOut(e.getTenantId(),
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
        notifications.fanOut(e.getTenantId(),
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
        // Overdue is high-priority — fan across every channel so the
        // user can't miss it. (Previously: EMAIL + SMS only.)
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.PAYMENT_OVERDUE,
                Map.of("amount",      safe(e.getAmount()),
                        "lateFee",    safe(e.getLateFee()),
                        "daysOverdue",safe(e.getDaysOverdue())));
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-payment-reminder",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentReminderEvent"}
    )
    public void onReminder(PaymentReminderEvent e) {
        if (e == null || !"payment.reminder".equals(e.getEventType())) return;
        log.info("Received {} for paymentId={}", e.getEventType(), e.getPaymentId());
        // NOTE: PaymentReminderEvent doesn't carry the rent amount today
        // (it only ships daysUntilDue + paymentId). The existing
        // payment-reminder-sms template references {{amount}} too —
        // that's a pre-existing template/event-shape mismatch; Mustache
        // renders the missing var as an empty string so the SMS reads
        // "Anirudh Homes: rent ₹ due in 5d." which is ugly but not broken.
        // Proper fix is to add `amount` to PaymentReminderEvent at the
        // producer side (payment-service); deferred to keep this change
        // surgical.
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.PAYMENT_REMINDER,
                Map.of("daysUntilDue", safe(e.getDaysUntilDue())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
