package com.spa.home_rental_application.payment_service.payment_service.entities;

import com.spa.home_rental_application.payment_service.payment_service.enums.ReminderStatus;
import com.spa.home_rental_application.payment_service.payment_service.enums.ReminderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payment_reminders", indexes = {
        @Index(name = "idx_reminders_payment", columnList = "payment_id"),
        @Index(name = "idx_reminders_status",  columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "reminder_date", nullable = false)
    private Instant reminderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ReminderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReminderStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
