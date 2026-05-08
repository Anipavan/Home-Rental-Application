package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.GstInvoiceGeneratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.ReraRegisteredEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code compliance-events} (published by Compliance Service).
 * <ul>
 *   <li>{@code rera.registered}        → confirmation to owner</li>
 *   <li>{@code gst.invoice.generated}  → invoice link to tenant (email + SMS)</li>
 * </ul>
 */
@Component
@Slf4j
public class ComplianceEventListener {

    private final NotificationService notifications;

    public ComplianceEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.compliance-topic:compliance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-rera-registered",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.ReraRegisteredEvent"}
    )
    public void onReraRegistered(ReraRegisteredEvent e) {
        if (e == null || !"rera.registered".equals(e.getEventType())) return;
        log.info("Received {} for propertyId={} state={}",
                e.getEventType(), e.getPropertyId(), e.getState());
        notifications.sendFromTemplate(e.getOwnerId(), NotificationType.EMAIL,
                NotificationCategory.RERA_REGISTERED,
                Map.of("propertyId",         safe(e.getPropertyId()),
                        "state",             safe(e.getState()),
                        "registrationNumber",safe(e.getReraRegistrationNumber()),
                        "expiryDate",        safe(e.getExpiryDate())));
    }

    @KafkaListener(
            topics = "${app.kafka.compliance-topic:compliance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-gst-invoice",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.GstInvoiceGeneratedEvent"}
    )
    public void onGstInvoice(GstInvoiceGeneratedEvent e) {
        if (e == null || !"gst.invoice.generated".equals(e.getEventType())) return;
        log.info("Received {} for invoiceId={} amount={}",
                e.getEventType(), e.getInvoiceId(), e.getTotalAmount());
        Map<String, Object> vars = Map.of(
                "invoiceNumber", safe(e.getInvoiceNumber()),
                "invoiceDate",   safe(e.getInvoiceDate()),
                "rentAmount",    safe(e.getRentAmount()),
                "gstApplicable", safe(e.getGstApplicable()),
                "gstAmount",     safe(e.getGstAmount()),
                "totalAmount",   safe(e.getTotalAmount()),
                "pdfUrl",        safe(e.getPdfUrl()));
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.GST_INVOICE_GENERATED, vars);
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.SMS,
                NotificationCategory.GST_INVOICE_GENERATED, vars);
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
