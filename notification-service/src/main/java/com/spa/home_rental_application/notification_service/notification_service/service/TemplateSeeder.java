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
        // ── One-time cleanup of stale seeds ──
        // The password-reset-email body shape evolved: the original
        // version only embedded {{token}}, the current version embeds
        // the full {{resetLink}} (so the recipient can click straight
        // through instead of pasting). seedIfAbsent below is a no-op
        // when a row exists, so without this cleanup users who booted
        // an earlier build keep getting the token-only body forever.
        //
        // The filter is deliberately narrow — we only delete the row
        // when the body is missing {{resetLink}}, so an admin who
        // edited the template via TemplateController and added the
        // placeholder themselves is left untouched.
        repo.findByCategoryAndType(NotificationCategory.PASSWORD_RESET, NotificationType.EMAIL)
                .filter(t -> t.getBodyTemplate() == null
                        || !t.getBodyTemplate().contains("{{resetLink}}"))
                .ifPresent(t -> {
                    log.info("Deleting stale password-reset-email template (missing resetLink placeholder) — will be re-seeded with the current body");
                    repo.delete(t);
                });

        seedIfAbsent("welcome-email", NotificationCategory.USER_REGISTRATION, NotificationType.EMAIL,
                "Welcome to Home Rental, {{userName}}",
                "Hi {{userName}},\n\nYour Home Rental account ({{email}}, role: {{role}}) is ready. Log in to get started.\n\n— Home Rental Team",
                List.of("userName", "email", "role"));

        seedIfAbsent("password-reset-email", NotificationCategory.PASSWORD_RESET, NotificationType.EMAIL,
                "Reset your Home Rental password",
                "Hi {{userName}},\n\nWe got a request to reset the password on your Hearth account.\n\n"
                        + "Click the link below to set a new password (valid until {{expiresAt}}):\n\n"
                        + "  {{resetLink}}\n\n"
                        + "If the link doesn't open, paste this token on the reset page instead:\n\n"
                        + "  {{token}}\n\n"
                        + "If you didn't request this, you can safely ignore this email — your "
                        + "current password is unchanged.",
                List.of("userName", "token", "resetLink", "expiresAt"));

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

        /* ─────────── SMS templates ───────────
         * Kept under 160 chars where possible so a single segment
         * goes out instead of a multipart split. WhatsApp templates
         * (next block) can be longer + use emoji safely.
         */
        seedIfAbsent("user-registration-sms", NotificationCategory.USER_REGISTRATION,
                NotificationType.SMS, null,
                "Welcome to Hearth, {{userName}}! Sign in to find your next home or list yours.",
                List.of("userName"));

        seedIfAbsent("payment-reminder-sms", NotificationCategory.PAYMENT_REMINDER,
                NotificationType.SMS, null,
                "Hearth: rent ₹{{amount}} due in {{daysUntilDue}}d. Pay via the app to avoid late fees.",
                List.of("amount", "daysUntilDue"));

        seedIfAbsent("payment-receipt-sms", NotificationCategory.PAYMENT_RECEIPT,
                NotificationType.SMS, null,
                "Hearth: payment of ₹{{amount}} received via {{method}}. Txn {{transactionId}}.",
                List.of("amount", "method", "transactionId"));

        seedIfAbsent("maintenance-created-sms", NotificationCategory.MAINTENANCE_CREATED,
                NotificationType.SMS, null,
                "Hearth: maintenance ticket {{requestNumber}} ({{category}}) opened. Track it in the app.",
                List.of("requestNumber", "category"));

        seedIfAbsent("maintenance-resolved-sms", NotificationCategory.MAINTENANCE_RESOLVED,
                NotificationType.SMS, null,
                "Hearth: ticket {{requestId}} resolved. Reply in the app if anything's still wrong.",
                List.of("requestId"));

        seedIfAbsent("complaint-created-sms", NotificationCategory.COMPLAINT_CREATED,
                NotificationType.SMS, null,
                "Hearth: complaint {{requestNumber}} filed. A manager will reply via the app.",
                List.of("requestNumber"));

        /* ─────────── WhatsApp templates ───────────
         * Twilio WhatsApp accepts the same template format as SMS.
         * Production-grade messaging would use Twilio-approved
         * templates; for now we lean on the session-window so any
         * inbound reply opens a 24h send window — enough for
         * transactional rentals flows.
         */
        seedIfAbsent("payment-reminder-whatsapp", NotificationCategory.PAYMENT_REMINDER,
                NotificationType.WHATSAPP, null,
                "Hi {{userName}} 👋\n\nYour rent of *₹{{amount}}* is due in *{{daysUntilDue}} days*.\n\n"
                        + "Tap the Pay Rent button in the Hearth app to settle it instantly. "
                        + "Reply STOP to mute these reminders.",
                List.of("userName", "amount", "daysUntilDue"));

        seedIfAbsent("payment-receipt-whatsapp", NotificationCategory.PAYMENT_RECEIPT,
                NotificationType.WHATSAPP, null,
                "Hi {{userName}} ✅\n\nWe've received your rent of *₹{{amount}}* via {{method}}.\n\n"
                        + "Transaction id: `{{transactionId}}`\nDate: {{paidDate}}\n\nThanks!",
                List.of("userName", "amount", "method", "transactionId", "paidDate"));

        seedIfAbsent("maintenance-created-whatsapp", NotificationCategory.MAINTENANCE_CREATED,
                NotificationType.WHATSAPP, null,
                "Hi 🛠️\n\nYour maintenance request *{{requestNumber}}* ({{category}}, "
                        + "{{priority}} priority) has been logged. A technician will be in touch shortly.\n\n"
                        + "Track or comment on it in the Hearth app under *Maintenance*.",
                List.of("requestNumber", "category", "priority"));

        seedIfAbsent("complaint-created-whatsapp", NotificationCategory.COMPLAINT_CREATED,
                NotificationType.WHATSAPP, null,
                "Hi 🔔\n\nWe've registered your complaint *{{requestNumber}}* about {{complaintCategory}}.\n\n"
                        + "A property manager will review it and reply through the Hearth in-app thread. "
                        + "You'll get a WhatsApp ping the moment they do.",
                List.of("requestNumber", "complaintCategory"));

        seedIfAbsent("lease-welcome-whatsapp", NotificationCategory.LEASE_WELCOME,
                NotificationType.WHATSAPP, null,
                "Welcome home {{userName}} 🏡\n\nYou've moved into *{{flatId}}*. "
                        + "Monthly rent is *₹{{rentAmount}}* starting {{startDate}}.\n\n"
                        + "Tap *Maintenance* in the app any time something needs fixing.",
                List.of("userName", "flatId", "rentAmount", "startDate"));

        // SMS leg of the lease-welcome — keeps the channel parity intact
        // (email + WhatsApp + SMS + bell) when an owner assigns a tenant
        // to a flat. Shorter copy because Twilio SMS is metered per 160
        // chars.
        seedIfAbsent("lease-welcome-sms", NotificationCategory.LEASE_WELCOME,
                NotificationType.SMS, null,
                "Welcome to your new home! Flat {{flatId}} is yours from {{startDate}}. "
                        + "Rent: Rs.{{rentAmount}}/mo. Manage everything in the Hearth app.",
                List.of("flatId", "rentAmount", "startDate"));

        /* ─────────── Welcome (registration) — SMS + WhatsApp legs ───────────
         * Email leg is already seeded as "welcome-email" near the top of
         * this method; adding the other two channels here so a brand-new
         * user gets a unified ping the moment they sign up.
         */
        seedIfAbsent("welcome-whatsapp", NotificationCategory.USER_REGISTRATION,
                NotificationType.WHATSAPP, null,
                "Welcome to Hearth, {{userName}}! 🏠\n\nYour account is ready. "
                        + "Open the app to find your next home or list one of your own.",
                List.of("userName", "email", "role"));

        /* ─────────── Visit-request flow ───────────
         * VISIT_REQUESTED  → ping the owner ("someone wants to see your flat")
         * VISIT_RESPONDED  → ping the visitor (confirmed / rescheduled / cancelled)
         * ENQUIRY_RECEIVED → ping the owner (contact-owner message)
         *
         * All three categories ship across every channel so the user
         * gets reached however they're configured. The "open the
         * Enquiries inbox" CTA stays consistent across copy variants.
         */
        seedIfAbsent("visit-requested-email", NotificationCategory.VISIT_REQUESTED,
                NotificationType.EMAIL,
                "New visit request for {{propertyLabel}}",
                "Hi,\n\n{{visitorName}} wants to visit {{propertyLabel}}"
                        + "{{#preferredAt}} on {{preferredAt}}{{/preferredAt}}.\n\n"
                        + "Open the Enquiries inbox in your Hearth dashboard to confirm "
                        + "or reschedule. Their message:\n\n\"{{message}}\"\n\n"
                        + "Reply fast — visitors who hear back within an hour are 3× more "
                        + "likely to sign a lease.",
                List.of("propertyLabel", "visitorName", "preferredAt", "message"));

        seedIfAbsent("visit-requested-sms", NotificationCategory.VISIT_REQUESTED,
                NotificationType.SMS, null,
                "Hearth: {{visitorName}} requested a visit to {{propertyLabel}}. "
                        + "Confirm in the app.",
                List.of("visitorName", "propertyLabel"));

        seedIfAbsent("visit-requested-whatsapp", NotificationCategory.VISIT_REQUESTED,
                NotificationType.WHATSAPP, null,
                "Hi 👋\n\n*{{visitorName}}* wants to see your flat — "
                        + "*{{propertyLabel}}*"
                        + "{{#preferredAt}} (preferred: {{preferredAt}}){{/preferredAt}}.\n\n"
                        + "Their message:\n>\n> {{message}}\n\n"
                        + "Open Enquiries in the Hearth app to confirm or reschedule.",
                List.of("visitorName", "propertyLabel", "preferredAt", "message"));

        seedIfAbsent("visit-responded-email", NotificationCategory.VISIT_RESPONDED,
                NotificationType.EMAIL,
                "Your visit request is {{status}}",
                "Hi {{visitorName}},\n\nYour request to visit "
                        + "{{propertyLabel}} is now *{{status}}*."
                        + "{{#adminResponse}}\n\nThe owner says: \"{{adminResponse}}\"{{/adminResponse}}\n\n"
                        + "See you soon!",
                List.of("visitorName", "propertyLabel", "status", "adminResponse"));

        seedIfAbsent("visit-responded-sms", NotificationCategory.VISIT_RESPONDED,
                NotificationType.SMS, null,
                "Hearth: your visit to {{propertyLabel}} is {{status}}. "
                        + "Check the app for details.",
                List.of("propertyLabel", "status"));

        seedIfAbsent("visit-responded-whatsapp", NotificationCategory.VISIT_RESPONDED,
                NotificationType.WHATSAPP, null,
                "Hi {{visitorName}} 👋\n\nYour request to visit *{{propertyLabel}}* "
                        + "is now *{{status}}*."
                        + "{{#adminResponse}}\n\nFrom the owner:\n> {{adminResponse}}{{/adminResponse}}\n\n"
                        + "See you soon!",
                List.of("visitorName", "propertyLabel", "status", "adminResponse"));

        seedIfAbsent("enquiry-received-email", NotificationCategory.ENQUIRY_RECEIVED,
                NotificationType.EMAIL,
                "New enquiry about {{propertyLabel}}",
                "Hi,\n\n{{visitorName}} just contacted you about "
                        + "{{propertyLabel}}.\n\nTheir message:\n\n\"{{message}}\"\n\n"
                        + "Reach out via the Hearth Enquiries inbox or directly "
                        + "by phone / email — both are in their contact card.",
                List.of("propertyLabel", "visitorName", "message"));

        seedIfAbsent("enquiry-received-sms", NotificationCategory.ENQUIRY_RECEIVED,
                NotificationType.SMS, null,
                "Hearth: new enquiry from {{visitorName}} about {{propertyLabel}}.",
                List.of("visitorName", "propertyLabel"));

        seedIfAbsent("enquiry-received-whatsapp", NotificationCategory.ENQUIRY_RECEIVED,
                NotificationType.WHATSAPP, null,
                "Hi 👋\n\n*{{visitorName}}* contacted you about *{{propertyLabel}}*:\n>\n"
                        + "> {{message}}\n\nReply via the Hearth Enquiries inbox.",
                List.of("visitorName", "propertyLabel", "message"));

        /* ───────────────────────────────────────────────────────────
         * Multi-channel fan-out templates for the listeners that
         * recently moved from sendFromTemplate(EMAIL) to fanOut(...).
         * Without these, the SMS / WhatsApp legs would fall back to
         * the generic "You have a new X notification" copy in
         * NotificationService.deliver (line ~243). Each block below
         * is in order: EMAIL → SMS → WhatsApp.
         * ─────────────────────────────────────────────────────────── */

        // ─── PAYMENT_CREATED — invoice just issued ───
        // EMAIL template already seeded near the top of this method.
        seedIfAbsent("payment-created-sms", NotificationCategory.PAYMENT_CREATED,
                NotificationType.SMS, null,
                "Hearth: new invoice {{invoiceNumber}} ₹{{amount}} due {{dueDate}}. Pay in the app.",
                List.of("invoiceNumber", "amount", "dueDate"));
        seedIfAbsent("payment-created-whatsapp", NotificationCategory.PAYMENT_CREATED,
                NotificationType.WHATSAPP, null,
                "Hi 👋\n\nA new rent invoice is ready for you.\n\n"
                        + "*Invoice:* {{invoiceNumber}}\n*Amount:* ₹{{amount}}\n*Due:* {{dueDate}}\n\n"
                        + "Open the Hearth app to pay via UPI, card, net-banking, or wallet.",
                List.of("invoiceNumber", "amount", "dueDate"));

        // ─── PAYMENT_OVERDUE — completing the channel set ───
        // EMAIL + SMS already seeded. WhatsApp filled in here so the
        // fanOut leg renders proper copy instead of generic fallback.
        seedIfAbsent("payment-overdue-whatsapp", NotificationCategory.PAYMENT_OVERDUE,
                NotificationType.WHATSAPP, null,
                "⚠️ *Rent overdue*\n\nYour rent is *{{daysOverdue}}* day(s) late.\n"
                        + "*Now owing:* ₹{{amount}} (rent) + ₹{{lateFee}} (late fee)\n\n"
                        + "Tap *Pay Rent* in the Hearth app to settle it before further charges accrue.",
                List.of("amount", "lateFee", "daysOverdue"));

        /* ─── LEASE_SIGNED — tenant just got a flat assigned ───
         * Variables come from LeaseEventListener.onSigned: leaseNumber,
         * startDate, endDate, rentAmount, deposit.
         */
        seedIfAbsent("lease-signed-email", NotificationCategory.LEASE_SIGNED,
                NotificationType.EMAIL,
                "Your lease {{leaseNumber}} is signed",
                "Congratulations — your tenancy is officially on.\n\n"
                        + "Lease number: {{leaseNumber}}\nStarts: {{startDate}}\n"
                        + "Ends:   {{endDate}}\nMonthly rent: ₹{{rentAmount}}\n"
                        + "Security deposit: ₹{{deposit}}\n\n"
                        + "You can download the signed lease PDF from the Hearth app "
                        + "under Documents at any time.",
                List.of("leaseNumber", "startDate", "endDate", "rentAmount", "deposit"));
        seedIfAbsent("lease-signed-sms", NotificationCategory.LEASE_SIGNED,
                NotificationType.SMS, null,
                "Hearth: lease {{leaseNumber}} signed. Starts {{startDate}}. Rent Rs.{{rentAmount}}/mo.",
                List.of("leaseNumber", "startDate", "rentAmount"));
        seedIfAbsent("lease-signed-whatsapp", NotificationCategory.LEASE_SIGNED,
                NotificationType.WHATSAPP, null,
                "🏡 *Lease signed*\n\nWelcome aboard! Your lease *{{leaseNumber}}* is live.\n\n"
                        + "*Starts:* {{startDate}}\n*Ends:* {{endDate}}\n"
                        + "*Rent:* ₹{{rentAmount}}/month\n*Deposit:* ₹{{deposit}}\n\n"
                        + "Download the signed PDF from *Documents* in the Hearth app.",
                List.of("leaseNumber", "startDate", "endDate", "rentAmount", "deposit"));

        /* ─── LEASE_EXPIRY — 60-day countdown (cron-driven) ───
         * Variables: endDate, daysUntilExpiry, rentAmount.
         */
        seedIfAbsent("lease-expiry-email", NotificationCategory.LEASE_EXPIRY,
                NotificationType.EMAIL,
                "Your lease ends in {{daysUntilExpiry}} days",
                "Heads-up — your lease is scheduled to end on {{endDate}} "
                        + "({{daysUntilExpiry}} days away).\n\n"
                        + "If you'd like to renew, open the Hearth app and tap *Renew lease* "
                        + "before then. If you're moving out, no action needed — your tenancy "
                        + "will close automatically and your security deposit refund will "
                        + "be initiated within 7 working days of move-out.",
                List.of("endDate", "daysUntilExpiry", "rentAmount"));
        seedIfAbsent("lease-expiry-sms", NotificationCategory.LEASE_EXPIRY,
                NotificationType.SMS, null,
                "Hearth: lease ends {{endDate}} ({{daysUntilExpiry}}d). Renew or move out in the app.",
                List.of("endDate", "daysUntilExpiry"));
        seedIfAbsent("lease-expiry-whatsapp", NotificationCategory.LEASE_EXPIRY,
                NotificationType.WHATSAPP, null,
                "📅 *Lease ending soon*\n\nYour lease ends on *{{endDate}}* "
                        + "({{daysUntilExpiry}} days from now).\n\n"
                        + "Want to stay? Tap *Renew lease* in the Hearth app.\n"
                        + "Moving on? No action needed — we'll handle the close-out + "
                        + "deposit refund automatically.",
                List.of("endDate", "daysUntilExpiry"));

        /* ─── LEASE_RENEWED — renewal confirmation ───
         * Variables: previousEndDate, newEndDate, previousRent, newRent.
         */
        seedIfAbsent("lease-renewed-email", NotificationCategory.LEASE_RENEWED,
                NotificationType.EMAIL,
                "Your lease is renewed",
                "Good news — your lease has been renewed.\n\n"
                        + "Previous end: {{previousEndDate}}\nNew end:      {{newEndDate}}\n"
                        + "Previous rent: ₹{{previousRent}}/month\n"
                        + "New rent:      ₹{{newRent}}/month\n\n"
                        + "The updated lease PDF is available under *Documents* in the Hearth app.",
                List.of("previousEndDate", "newEndDate", "previousRent", "newRent"));
        seedIfAbsent("lease-renewed-sms", NotificationCategory.LEASE_RENEWED,
                NotificationType.SMS, null,
                "Hearth: lease renewed until {{newEndDate}}. New rent Rs.{{newRent}}/mo.",
                List.of("newEndDate", "newRent"));
        seedIfAbsent("lease-renewed-whatsapp", NotificationCategory.LEASE_RENEWED,
                NotificationType.WHATSAPP, null,
                "♻️ *Lease renewed*\n\nWelcome back for another term.\n\n"
                        + "*New end date:* {{newEndDate}}\n*New rent:* ₹{{newRent}}/month\n\n"
                        + "Download the updated lease PDF from *Documents* in the Hearth app.",
                List.of("newEndDate", "newRent"));

        /* ─── LEASE_TERMINATED — used by both LeaseEventListener.onTerminated
         * (lease-service-driven termination) AND PropertyEventListener.onFlatVacated
         * (property-service-driven vacate). Common vars: terminatedOn,
         * terminationReason. Flat-id only present on the flat.vacated path
         * (Mustache handles the missing var gracefully — empty string).
         */
        seedIfAbsent("lease-terminated-email", NotificationCategory.LEASE_TERMINATED,
                NotificationType.EMAIL,
                "Your tenancy has ended",
                "Hi,\n\nWe've recorded that your tenancy ended on {{terminatedOn}} "
                        + "(reason: {{terminationReason}}).\n\n"
                        + "Next steps:\n"
                        + "• If a security deposit was held, refund will be initiated within "
                        + "7 working days to the bank account on file.\n"
                        + "• Your final payment statement is now in *Documents* in the Hearth app.\n"
                        + "• Need anything? Reply to this email or message us in the app.\n\n"
                        + "Thanks for staying with Hearth.",
                List.of("terminatedOn", "terminationReason", "flatId"));
        seedIfAbsent("lease-terminated-sms", NotificationCategory.LEASE_TERMINATED,
                NotificationType.SMS, null,
                "Hearth: your tenancy ended {{terminatedOn}}. Deposit refund (if any) in 7 working days.",
                List.of("terminatedOn"));
        seedIfAbsent("lease-terminated-whatsapp", NotificationCategory.LEASE_TERMINATED,
                NotificationType.WHATSAPP, null,
                "👋 *Tenancy ended*\n\nYour tenancy ended on *{{terminatedOn}}* "
                        + "(_{{terminationReason}}_).\n\n"
                        + "If a security deposit was held, we'll refund it to your bank "
                        + "account on file within 7 working days. Your final statement "
                        + "is in *Documents* in the Hearth app.\n\nThanks for staying with us. 🙏",
                List.of("terminatedOn", "terminationReason"));

        /* ─── MAINTENANCE_ASSIGNED / MAINTENANCE_RESOLVED — completing
         * the channel set for the in-flight maintenance pipeline.
         * MaintenanceEventListener already uses fanOut for these, so
         * without SMS/WhatsApp templates the legs render generic copy.
         */
        seedIfAbsent("maintenance-assigned-sms", NotificationCategory.MAINTENANCE_ASSIGNED,
                NotificationType.SMS, null,
                "Hearth: ticket {{requestId}} assigned to {{assignedTo}}. They'll be in touch.",
                List.of("requestId", "assignedTo"));
        seedIfAbsent("maintenance-assigned-whatsapp", NotificationCategory.MAINTENANCE_ASSIGNED,
                NotificationType.WHATSAPP, null,
                "🛠️ *Maintenance update*\n\nTicket *{{requestId}}* is assigned to "
                        + "*{{assignedTo}}*. They'll reach out shortly — track or "
                        + "comment on it in the Hearth app under *Maintenance*.",
                List.of("requestId", "assignedTo"));
        seedIfAbsent("maintenance-resolved-whatsapp", NotificationCategory.MAINTENANCE_RESOLVED,
                NotificationType.WHATSAPP, null,
                "✅ *Resolved*\n\nGreat news — ticket *{{requestId}}* is resolved "
                        + "(turnaround {{resolutionTimeMinutes}} minutes).\n\n"
                        + "Reply in the Hearth app if anything's still off.",
                List.of("requestId", "resolutionTimeMinutes"));

        /* ─── COMPLAINT_ACKNOWLEDGED / COMPLAINT_RESOLVED — same
         * reasoning: MaintenanceEventListener fans these out across
         * all channels; templates filled in here.
         */
        seedIfAbsent("complaint-acknowledged-sms", NotificationCategory.COMPLAINT_ACKNOWLEDGED,
                NotificationType.SMS, null,
                "Hearth: complaint {{requestNumber}} is being handled by {{assignedTo}}.",
                List.of("requestNumber", "assignedTo"));
        seedIfAbsent("complaint-acknowledged-whatsapp", NotificationCategory.COMPLAINT_ACKNOWLEDGED,
                NotificationType.WHATSAPP, null,
                "🔔 *Complaint update*\n\nYour complaint *{{requestNumber}}* is now being "
                        + "worked on by *{{assignedTo}}*. You'll be pinged when it's resolved.",
                List.of("requestNumber", "assignedTo"));
        seedIfAbsent("complaint-resolved-sms", NotificationCategory.COMPLAINT_RESOLVED,
                NotificationType.SMS, null,
                "Hearth: complaint {{requestNumber}} resolved. Reply in the app to re-open within 7d.",
                List.of("requestNumber"));
        seedIfAbsent("complaint-resolved-whatsapp", NotificationCategory.COMPLAINT_RESOLVED,
                NotificationType.WHATSAPP, null,
                "✅ *Complaint resolved*\n\nYour complaint *{{requestNumber}}* is closed.\n\n"
                        + "Not happy with the outcome? Reply in the Hearth app within 7 days "
                        + "and we'll re-open it.",
                List.of("requestNumber"));
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
