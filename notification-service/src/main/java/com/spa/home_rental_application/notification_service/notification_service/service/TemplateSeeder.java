package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds reasonable default templates on startup so the service is
 * useful out of the box. Idempotent — only inserts if the (category, type)
 * row is missing. Operators can later edit/replace templates via the
 * admin endpoints in {@code TemplateController}.
 */
@Component
@Slf4j
public class TemplateSeeder {

    private final NotificationTemplateRepository repo;

    public TemplateSeeder(NotificationTemplateRepository repo) {
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedIfAbsent("welcome-email", NotificationCategory.USER_REGISTRATION, NotificationType.EMAIL,
                "Welcome to Home Rental, {{userName}}",
                "Hi {{userName}},\n\nYour Home Rental account ({{email}}, role: {{role}}) is ready. Log in to get started.\n\n— Home Rental Team",
                List.of("userName", "email", "role"));

        seedIfAbsent("password-reset-email", NotificationCategory.PASSWORD_RESET, NotificationType.EMAIL,
                "Reset your Home Rental password",
                "Hi {{userName}},\n\nUse this token to reset your password (valid until {{expiresAt}}):\n\n  {{token}}\n\nIf you didn't request this, ignore this email.",
                List.of("userName", "token", "expiresAt"));

        seedIfAbsent("payment-created-email", NotificationCategory.PAYMENT_CREATED, NotificationType.EMAIL,
                "New invoice {{invoiceNumber}} — ₹{{amount}} due {{dueDate}}",
                "A new rent invoice has been generated for you.\n\nInvoice: {{invoiceNumber}}\nAmount: ₹{{amount}}\nDue date: {{dueDate}}\n\nLog in to pay via UPI, card, net-banking, or wallet.",
                List.of("invoiceNumber", "amount", "dueDate"));

        seedIfAbsent("payment-reminder-email", NotificationCategory.PAYMENT_REMINDER, NotificationType.EMAIL,
                "Reminder: rent payment due in {{daysUntilDue}} day(s)",
                "Friendly reminder: your rent payment is due in {{daysUntilDue}} day(s). Pay early to avoid late fees.",
                List.of("daysUntilDue"));

        seedIfAbsent("payment-overdue-email", NotificationCategory.PAYMENT_OVERDUE, NotificationType.EMAIL,
                "Overdue rent payment — ₹{{amount}} + ₹{{lateFee}} late fee",
                "Your rent payment is now {{daysOverdue}} day(s) overdue. Total payable: ₹{{amount}} (rent) + ₹{{lateFee}} (late fee). Please pay immediately to avoid further charges.",
                List.of("amount", "lateFee", "daysOverdue"));
        seedIfAbsent("payment-overdue-sms", NotificationCategory.PAYMENT_OVERDUE, NotificationType.SMS,
                null,
                "HomeRental: rent overdue {{daysOverdue}}d. Pay ₹{{amount}}+₹{{lateFee}} late fee now to avoid more charges.",
                List.of("amount", "lateFee", "daysOverdue"));

        seedIfAbsent("payment-receipt-email", NotificationCategory.PAYMENT_RECEIPT, NotificationType.EMAIL,
                "Payment received — ₹{{amount}} via {{method}}",
                "Thanks! We've received your rent payment of ₹{{amount}} via {{method}}.\n\nTransaction id: {{transactionId}}\nDate: {{paidDate}}",
                List.of("amount", "method", "transactionId", "paidDate"));

        seedIfAbsent("maintenance-created-email", NotificationCategory.MAINTENANCE_CREATED, NotificationType.EMAIL,
                "Maintenance request {{requestNumber}} received",
                "We've received your maintenance request {{requestNumber}} (category: {{category}}, priority: {{priority}}). We'll keep you posted.",
                List.of("requestNumber", "category", "priority"));

        seedIfAbsent("maintenance-assigned-email", NotificationCategory.MAINTENANCE_ASSIGNED, NotificationType.EMAIL,
                "Maintenance request assigned",
                "Maintenance request {{requestId}} has been assigned to {{assignedTo}}. They'll reach out shortly.",
                List.of("requestId", "assignedTo"));

        seedIfAbsent("maintenance-resolved-email", NotificationCategory.MAINTENANCE_RESOLVED, NotificationType.EMAIL,
                "Maintenance request resolved",
                "Good news — your maintenance request {{requestId}} has been resolved (resolution time: {{resolutionTimeMinutes}} minutes).",
                List.of("requestId", "resolutionTimeMinutes"));

        /* ─────── Complaints — share the maintenance pipeline, distinct copy ─────── */
        seedIfAbsent("complaint-created-email", NotificationCategory.COMPLAINT_CREATED, NotificationType.EMAIL,
                "Complaint {{requestNumber}} registered",
                "We've registered your complaint {{requestNumber}} (about: {{complaintCategory}}, priority: {{priority}}).\n\n"
                        + "A property manager will review it and reply through the in-app messages thread. "
                        + "You can track status anytime under Complaints in your dashboard.",
                List.of("requestNumber", "complaintCategory", "priority"));

        seedIfAbsent("complaint-acknowledged-email", NotificationCategory.COMPLAINT_ACKNOWLEDGED, NotificationType.EMAIL,
                "Update on complaint {{requestNumber}}",
                "Your complaint {{requestNumber}} is now being worked on by {{assignedTo}}. "
                        + "You'll be notified when there's a resolution; replies appear in the complaint's messages thread.",
                List.of("requestNumber", "assignedTo"));

        seedIfAbsent("complaint-resolved-email", NotificationCategory.COMPLAINT_RESOLVED, NotificationType.EMAIL,
                "Your complaint has been resolved",
                "Good news — your complaint {{requestNumber}} has been resolved. "
                        + "If you're not happy with the outcome, reply in the messages thread within 7 days to re-open it.",
                List.of("requestNumber", "resolutionTimeMinutes"));

        seedIfAbsent("welcome-flat-email", NotificationCategory.LEASE_WELCOME, NotificationType.EMAIL,
                "Welcome to your new home!",
                "Welcome! You've moved into flat {{flatId}}. Your monthly rent is ₹{{rentAmount}} starting {{startDate}}.",
                List.of("flatId", "rentAmount", "startDate"));
    }

    private void seedIfAbsent(String name,
                              NotificationCategory category,
                              NotificationType type,
                              String subject,
                              String bodyTemplate,
                              List<String> variables) {
        if (repo.findByCategoryAndType(category, type).isPresent()) return;
        repo.save(NotificationTemplate.builder()
                .name(name)
                .category(category)
                .type(type)
                .subject(subject)
                .bodyTemplate(bodyTemplate)
                .variables(variables)
                .build());
        log.info("Seeded notification template: {} ({} / {})", name, category, type);
    }
}
