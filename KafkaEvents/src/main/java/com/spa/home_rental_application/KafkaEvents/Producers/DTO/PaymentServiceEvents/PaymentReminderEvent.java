package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReminderEvent {
    private String eventType;       // "payment.reminder"
    private String paymentId;
    private String tenantId;
    private String reminderType;    // EMAIL, SMS, PUSH
    private long daysUntilDue;

    /**
     * Total payable (base rent + late fee if any). Populated by the
     * reminder scheduler so the notification-service can render
     * {@code {{amount}}} in the reminder email / SMS / WhatsApp
     * templates. Nullable for backward-compatibility with events
     * produced before this field existed.
     */
    private BigDecimal amount;

    /**
     * Original due date of the invoice. Lets the reminder template
     * say "due {{dueDate}}" instead of relying only on the relative
     * {{daysUntilDue}} count. Nullable for backward-compatibility.
     */
    private LocalDate dueDate;

    private Instant timestamp;
}
