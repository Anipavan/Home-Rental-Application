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

    /**
     * Sentinel marker baked into every HTML-shell template body. The
     * 2026-05 redesign moved EMAIL templates from plain-text +
     * runtime-wrapped brand shell to fully-pre-rendered HTML emitted
     * by {@link EmailTemplateBuilder}. Any template row whose body
     * lacks this marker is considered legacy and gets refreshed on
     * boot. Admin-edited templates that still want the new shell can
     * include the marker themselves; admin-edited templates that
     * intentionally use plain text won't include it and stay
     * untouched as long as the marker is missing AND the admin row
     * looks unlike the default seed (length/content heuristics
     * deliberately not added — admins who want full control should
     * call the template API to lock their changes).
     */
    private static final String HTML_TEMPLATE_MARKER = "<!--anirudhhomes-html-shell-v1-->";

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

        // ── 2026-05: HTML-shell migration ──
        // Refresh the major EMAIL templates to their HTML-rendered
        // versions whenever the current row lacks the sentinel
        // marker. Each call below is an explicit category refresh —
        // calling them individually rather than walking every row
        // means a one-off admin-edited template stays put.
        upgradeEmailToHtml(NotificationCategory.USER_REGISTRATION,
                "Welcome to Anirudh Homes — your account is ready",
                buildWelcomeEmailHtml());
        upgradeEmailToHtml(NotificationCategory.PASSWORD_RESET,
                "Reset your Anirudh Homes password",
                buildPasswordResetEmailHtml());
        upgradeEmailToHtml(NotificationCategory.PAYMENT_CREATED,
                "New rent invoice — ₹{{amount}} due {{dueDate}}",
                buildPaymentCreatedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.PAYMENT_REMINDER,
                "Rent due in {{daysUntilDue}} day(s) — pay early",
                buildPaymentReminderEmailHtml());
        upgradeEmailToHtml(NotificationCategory.PAYMENT_OVERDUE,
                "Rent overdue — ₹{{amount}} + ₹{{lateFee}} late fee",
                buildPaymentOverdueEmailHtml());
        upgradeEmailToHtml(NotificationCategory.PAYMENT_RECEIPT,
                "Payment received — ₹{{amount}}",
                buildPaymentReceiptEmailHtml());
        upgradeEmailToHtml(NotificationCategory.PAYMENT_RECEIVED_FOR_OWNER,
                "Rent received — ₹{{amount}} via {{method}}",
                buildPaymentReceivedOwnerEmailHtml());
        upgradeEmailToHtml(NotificationCategory.MAINTENANCE_CREATED,
                "Maintenance request {{requestNumber}} received",
                buildMaintenanceCreatedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.MAINTENANCE_ASSIGNED,
                "Maintenance request assigned",
                buildMaintenanceAssignedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.MAINTENANCE_RESOLVED,
                "Maintenance request resolved",
                buildMaintenanceResolvedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.COMPLAINT_CREATED,
                "Complaint {{requestNumber}} registered",
                buildComplaintCreatedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.COMPLAINT_ACKNOWLEDGED,
                "Update on complaint {{requestNumber}}",
                buildComplaintAcknowledgedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.COMPLAINT_RESOLVED,
                "Your complaint has been resolved",
                buildComplaintResolvedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.LEASE_SIGNED,
                "Your lease {{leaseNumber}} is signed",
                buildLeaseSignedEmailHtml());
        // Owner-side mirrors. Different copy ("your tenant raised X")
        // and a CTA that points the owner at their tenant-detail or
        // ticket-management screen rather than the tenant's view.
        // Subject lines use Mustache truthy sections so we degrade
        // gracefully when the tenant name isn't on the event (the
        // KafkaEvents DTOs don't carry it yet — listener passes empty
        // string and the section is hidden).
        upgradeEmailToHtml(NotificationCategory.MAINTENANCE_RAISED_FOR_OWNER,
                "New maintenance request on your property{{#tenantName}} — from {{tenantName}}{{/tenantName}} ({{requestNumber}})",
                buildMaintenanceRaisedForOwnerEmailHtml());
        upgradeEmailToHtml(NotificationCategory.COMPLAINT_RAISED_FOR_OWNER,
                "New complaint on your property{{#tenantName}} — from {{tenantName}}{{/tenantName}} ({{requestNumber}})",
                buildComplaintRaisedForOwnerEmailHtml());
        upgradeEmailToHtml(NotificationCategory.REVIEW_RECEIVED_FOR_OWNER,
                "New review on your property{{#tenantName}} — from {{tenantName}}{{/tenantName}}",
                buildReviewReceivedForOwnerEmailHtml());
        // Tenant-facing document approval / rejection. Owner reviews
        // each uploaded doc; rejection emails carry the reason +
        // a clear re-upload CTA so the tenant doesn't have to hunt
        // for the right screen.
        upgradeEmailToHtml(NotificationCategory.DOCUMENT_APPROVED,
                "Your {{documentType}} was approved",
                buildDocumentApprovedEmailHtml());
        upgradeEmailToHtml(NotificationCategory.DOCUMENT_REJECTED,
                "Your {{documentType}} needs another look — please re-upload",
                buildDocumentRejectedEmailHtml());

        // ── LEASE_WELCOME templates: switched from {{flatId}} (raw UUID)
        // to {{flatNumber}} (human-readable, e.g. "A-301"). UPDATE the
        // existing row in place rather than DELETE-then-INSERT — the
        // previous delete+re-seed approach occasionally left the row
        // missing entirely (an event slipped in between the delete and
        // the seedIfAbsent insert, falling back to the generic "You
        // have a new X notification" copy). Updating in place removes
        // that gap window. Still narrow — only touches rows that still
        // embed the old {{flatId}} placeholder OR are missing the new
        // {{signInUrl}} CTA (Issue #7), so admin-edited templates that
        // already include both stay untouched.
        refreshLeaseWelcomeIfStale(NotificationCategory.LEASE_WELCOME,
                NotificationType.EMAIL,
                "welcome-flat-email",
                "Welcome to your new home!",
                "Welcome! You've moved into flat {{flatNumber}}.\n\n"
                        + "Your monthly rent is ₹{{rentAmount}} starting {{startDate}}.\n\n"
                        + "Sign in to view your lease, pay rent, and raise maintenance tickets:\n"
                        + "  {{signInUrl}}",
                List.of("flatNumber", "rentAmount", "startDate", "signInUrl"));
        refreshLeaseWelcomeIfStale(NotificationCategory.LEASE_WELCOME,
                NotificationType.SMS,
                "lease-welcome-sms",
                null,
                "Welcome to your new home! Flat {{flatNumber}} is yours from {{startDate}}. "
                        + "Rent Rs.{{rentAmount}}/mo. Sign in: {{signInUrl}}",
                List.of("flatNumber", "rentAmount", "startDate", "signInUrl"));
        refreshLeaseWelcomeIfStale(NotificationCategory.LEASE_WELCOME,
                NotificationType.WHATSAPP,
                "lease-welcome-whatsapp",
                null,
                "Welcome home 🏡\n\nYou've moved into *flat {{flatNumber}}*. "
                        + "Monthly rent is *₹{{rentAmount}}* starting {{startDate}}.\n\n"
                        + "Sign in to view your lease 👉 {{signInUrl}}",
                List.of("flatNumber", "rentAmount", "startDate", "signInUrl"));

        // ── USER_REGISTRATION templates: add a sign-in CTA (Issue #7)
        // so the welcome message links straight back to the app. Same
        // in-place refresh trick as LEASE_WELCOME — only touches rows
        // that still lack the {{signInUrl}} placeholder, so admin-
        // edited templates are left alone.
        refreshUserRegistrationIfStale(NotificationCategory.USER_REGISTRATION,
                NotificationType.EMAIL,
                "welcome-email",
                "Welcome to Home Rental, {{userName}}",
                "Hi {{userName}},\n\nYour Home Rental account ({{email}}, role: {{role}}) is ready.\n\n"
                        + "Sign in to start finding your next home or list one of your own:\n"
                        + "  {{signInUrl}}\n\n— Home Rental Team",
                List.of("userName", "email", "role", "signInUrl"));
        refreshUserRegistrationIfStale(NotificationCategory.USER_REGISTRATION,
                NotificationType.SMS,
                "user-registration-sms",
                null,
                "Welcome to Anirudh Homes, {{userName}}! Sign in: {{signInUrl}}",
                List.of("userName", "signInUrl"));
        refreshUserRegistrationIfStale(NotificationCategory.USER_REGISTRATION,
                NotificationType.WHATSAPP,
                "welcome-whatsapp",
                null,
                "Welcome to Anirudh Homes, {{userName}}! 🏠\n\nYour account is ready. "
                        + "Sign in here 👉 {{signInUrl}}",
                List.of("userName", "email", "role", "signInUrl"));

        seedIfAbsent("welcome-email", NotificationCategory.USER_REGISTRATION, NotificationType.EMAIL,
                "Welcome to Home Rental, {{userName}}",
                "Hi {{userName}},\n\nYour Home Rental account ({{email}}, role: {{role}}) is ready. Log in to get started.\n\n— Home Rental Team",
                List.of("userName", "email", "role"));

        seedIfAbsent("password-reset-email", NotificationCategory.PASSWORD_RESET, NotificationType.EMAIL,
                "Reset your Home Rental password",
                "Hi {{userName}},\n\nWe got a request to reset the password on your Anirudh Homes account.\n\n"
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

        // Owner-side mirror of the receipt — fired alongside, recipient is
        // the owner. "Rent received" framing keeps the message scannable
        // in a busy inbox; full details (txn id, method) included for the
        // accountant who reconciles them against the bank statement.
        seedIfAbsent("payment-received-owner-email",
                NotificationCategory.PAYMENT_RECEIVED_FOR_OWNER, NotificationType.EMAIL,
                "Rent received — ₹{{amount}} via {{method}}",
                "Good news — rent of ₹{{amount}} has been received via {{method}}.\n\n"
                        + "Transaction id: {{transactionId}}\nDate: {{paidDate}}\n\n"
                        + "View full details in your Anirudh Homes dashboard.",
                List.of("amount", "method", "transactionId", "paidDate", "paymentId"));

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
                "Welcome! You've moved into flat {{flatNumber}}. Your monthly rent is ₹{{rentAmount}} starting {{startDate}}.",
                List.of("flatNumber", "rentAmount", "startDate"));

        /* ─────────── SMS templates ───────────
         * Kept under 160 chars where possible so a single segment
         * goes out instead of a multipart split. WhatsApp templates
         * (next block) can be longer + use emoji safely.
         */
        seedIfAbsent("user-registration-sms", NotificationCategory.USER_REGISTRATION,
                NotificationType.SMS, null,
                "Welcome to Anirudh Homes, {{userName}}! Sign in to find your next home or list yours.",
                List.of("userName"));

        seedIfAbsent("payment-reminder-sms", NotificationCategory.PAYMENT_REMINDER,
                NotificationType.SMS, null,
                "Anirudh Homes: rent ₹{{amount}} due in {{daysUntilDue}}d. Pay via the app to avoid late fees.",
                List.of("amount", "daysUntilDue"));

        seedIfAbsent("payment-receipt-sms", NotificationCategory.PAYMENT_RECEIPT,
                NotificationType.SMS, null,
                "Anirudh Homes: payment of ₹{{amount}} received via {{method}}. Txn {{transactionId}}.",
                List.of("amount", "method", "transactionId"));

        seedIfAbsent("payment-received-owner-sms",
                NotificationCategory.PAYMENT_RECEIVED_FOR_OWNER, NotificationType.SMS, null,
                "Anirudh Homes: rent of ₹{{amount}} received via {{method}}. Txn {{transactionId}}.",
                List.of("amount", "method", "transactionId"));

        seedIfAbsent("maintenance-created-sms", NotificationCategory.MAINTENANCE_CREATED,
                NotificationType.SMS, null,
                "Anirudh Homes: maintenance ticket {{requestNumber}} ({{category}}) opened. Track it in the app.",
                List.of("requestNumber", "category"));

        seedIfAbsent("maintenance-resolved-sms", NotificationCategory.MAINTENANCE_RESOLVED,
                NotificationType.SMS, null,
                "Anirudh Homes: ticket {{requestId}} resolved. Reply in the app if anything's still wrong.",
                List.of("requestId"));

        seedIfAbsent("complaint-created-sms", NotificationCategory.COMPLAINT_CREATED,
                NotificationType.SMS, null,
                "Anirudh Homes: complaint {{requestNumber}} filed. A manager will reply via the app.",
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
                        + "Tap the Pay Rent button in the Anirudh Homes app to settle it instantly. "
                        + "Reply STOP to mute these reminders.",
                List.of("userName", "amount", "daysUntilDue"));

        seedIfAbsent("payment-receipt-whatsapp", NotificationCategory.PAYMENT_RECEIPT,
                NotificationType.WHATSAPP, null,
                "Hi {{userName}} ✅\n\nWe've received your rent of *₹{{amount}}* via {{method}}.\n\n"
                        + "Transaction id: `{{transactionId}}`\nDate: {{paidDate}}\n\nThanks!",
                List.of("userName", "amount", "method", "transactionId", "paidDate"));

        seedIfAbsent("payment-received-owner-whatsapp",
                NotificationCategory.PAYMENT_RECEIVED_FOR_OWNER, NotificationType.WHATSAPP, null,
                "Hi {{userName}} 💰\n\nRent of *₹{{amount}}* received via {{method}}.\n\n"
                        + "Transaction id: `{{transactionId}}`\nDate: {{paidDate}}\n\n"
                        + "Open the Anirudh Homes app for the full payment view.",
                List.of("userName", "amount", "method", "transactionId", "paidDate"));

        seedIfAbsent("maintenance-created-whatsapp", NotificationCategory.MAINTENANCE_CREATED,
                NotificationType.WHATSAPP, null,
                "Hi 🛠️\n\nYour maintenance request *{{requestNumber}}* ({{category}}, "
                        + "{{priority}} priority) has been logged. A technician will be in touch shortly.\n\n"
                        + "Track or comment on it in the Anirudh Homes app under *Maintenance*.",
                List.of("requestNumber", "category", "priority"));

        seedIfAbsent("complaint-created-whatsapp", NotificationCategory.COMPLAINT_CREATED,
                NotificationType.WHATSAPP, null,
                "Hi 🔔\n\nWe've registered your complaint *{{requestNumber}}* about {{complaintCategory}}.\n\n"
                        + "A property manager will review it and reply through the Anirudh Homes in-app thread. "
                        + "You'll get a WhatsApp ping the moment they do.",
                List.of("requestNumber", "complaintCategory"));

        seedIfAbsent("lease-welcome-whatsapp", NotificationCategory.LEASE_WELCOME,
                NotificationType.WHATSAPP, null,
                "Welcome home 🏡\n\nYou've moved into *flat {{flatNumber}}*. "
                        + "Monthly rent is *₹{{rentAmount}}* starting {{startDate}}.\n\n"
                        + "Tap *Maintenance* in the app any time something needs fixing.",
                List.of("flatNumber", "rentAmount", "startDate"));

        // SMS leg of the lease-welcome — keeps the channel parity intact
        // (email + WhatsApp + SMS + bell) when an owner assigns a tenant
        // to a flat. Shorter copy because Twilio SMS is metered per 160
        // chars.
        seedIfAbsent("lease-welcome-sms", NotificationCategory.LEASE_WELCOME,
                NotificationType.SMS, null,
                "Welcome to your new home! Flat {{flatNumber}} is yours from {{startDate}}. "
                        + "Rent: Rs.{{rentAmount}}/mo. Manage everything in the Anirudh Homes app.",
                List.of("flatNumber", "rentAmount", "startDate"));

        /* ─────────── Welcome (registration) — SMS + WhatsApp legs ───────────
         * Email leg is already seeded as "welcome-email" near the top of
         * this method; adding the other two channels here so a brand-new
         * user gets a unified ping the moment they sign up.
         */
        seedIfAbsent("welcome-whatsapp", NotificationCategory.USER_REGISTRATION,
                NotificationType.WHATSAPP, null,
                "Welcome to Anirudh Homes, {{userName}}! 🏠\n\nYour account is ready. "
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
                        + "Open the Enquiries inbox in your Anirudh Homes dashboard to confirm "
                        + "or reschedule. Their message:\n\n\"{{message}}\"\n\n"
                        + "Reply fast — visitors who hear back within an hour are 3× more "
                        + "likely to sign a lease.",
                List.of("propertyLabel", "visitorName", "preferredAt", "message"));

        seedIfAbsent("visit-requested-sms", NotificationCategory.VISIT_REQUESTED,
                NotificationType.SMS, null,
                "Anirudh Homes: {{visitorName}} requested a visit to {{propertyLabel}}. "
                        + "Confirm in the app.",
                List.of("visitorName", "propertyLabel"));

        seedIfAbsent("visit-requested-whatsapp", NotificationCategory.VISIT_REQUESTED,
                NotificationType.WHATSAPP, null,
                "Hi 👋\n\n*{{visitorName}}* wants to see your flat — "
                        + "*{{propertyLabel}}*"
                        + "{{#preferredAt}} (preferred: {{preferredAt}}){{/preferredAt}}.\n\n"
                        + "Their message:\n>\n> {{message}}\n\n"
                        + "Open Enquiries in the Anirudh Homes app to confirm or reschedule.",
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
                "Anirudh Homes: your visit to {{propertyLabel}} is {{status}}. "
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
                        + "Reach out via the Anirudh Homes Enquiries inbox or directly "
                        + "by phone / email — both are in their contact card.",
                List.of("propertyLabel", "visitorName", "message"));

        seedIfAbsent("enquiry-received-sms", NotificationCategory.ENQUIRY_RECEIVED,
                NotificationType.SMS, null,
                "Anirudh Homes: new enquiry from {{visitorName}} about {{propertyLabel}}.",
                List.of("visitorName", "propertyLabel"));

        seedIfAbsent("enquiry-received-whatsapp", NotificationCategory.ENQUIRY_RECEIVED,
                NotificationType.WHATSAPP, null,
                "Hi 👋\n\n*{{visitorName}}* contacted you about *{{propertyLabel}}*:\n>\n"
                        + "> {{message}}\n\nReply via the Anirudh Homes Enquiries inbox.",
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
                "Anirudh Homes: new invoice {{invoiceNumber}} ₹{{amount}} due {{dueDate}}. Pay in the app.",
                List.of("invoiceNumber", "amount", "dueDate"));
        seedIfAbsent("payment-created-whatsapp", NotificationCategory.PAYMENT_CREATED,
                NotificationType.WHATSAPP, null,
                "Hi 👋\n\nA new rent invoice is ready for you.\n\n"
                        + "*Invoice:* {{invoiceNumber}}\n*Amount:* ₹{{amount}}\n*Due:* {{dueDate}}\n\n"
                        + "Open the Anirudh Homes app to pay via UPI, card, net-banking, or wallet.",
                List.of("invoiceNumber", "amount", "dueDate"));

        // ─── PAYMENT_OVERDUE — completing the channel set ───
        // EMAIL + SMS already seeded. WhatsApp filled in here so the
        // fanOut leg renders proper copy instead of generic fallback.
        seedIfAbsent("payment-overdue-whatsapp", NotificationCategory.PAYMENT_OVERDUE,
                NotificationType.WHATSAPP, null,
                "⚠️ *Rent overdue*\n\nYour rent is *{{daysOverdue}}* day(s) late.\n"
                        + "*Now owing:* ₹{{amount}} (rent) + ₹{{lateFee}} (late fee)\n\n"
                        + "Tap *Pay Rent* in the Anirudh Homes app to settle it before further charges accrue.",
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
                        + "You can download the signed lease PDF from the Anirudh Homes app "
                        + "under Documents at any time.",
                List.of("leaseNumber", "startDate", "endDate", "rentAmount", "deposit"));
        seedIfAbsent("lease-signed-sms", NotificationCategory.LEASE_SIGNED,
                NotificationType.SMS, null,
                "Anirudh Homes: lease {{leaseNumber}} signed. Starts {{startDate}}. Rent Rs.{{rentAmount}}/mo.",
                List.of("leaseNumber", "startDate", "rentAmount"));
        seedIfAbsent("lease-signed-whatsapp", NotificationCategory.LEASE_SIGNED,
                NotificationType.WHATSAPP, null,
                "🏡 *Lease signed*\n\nWelcome aboard! Your lease *{{leaseNumber}}* is live.\n\n"
                        + "*Starts:* {{startDate}}\n*Ends:* {{endDate}}\n"
                        + "*Rent:* ₹{{rentAmount}}/month\n*Deposit:* ₹{{deposit}}\n\n"
                        + "Download the signed PDF from *Documents* in the Anirudh Homes app.",
                List.of("leaseNumber", "startDate", "endDate", "rentAmount", "deposit"));

        /* ─── LEASE_EXPIRY — 60-day countdown (cron-driven) ───
         * Variables: endDate, daysUntilExpiry, rentAmount.
         */
        seedIfAbsent("lease-expiry-email", NotificationCategory.LEASE_EXPIRY,
                NotificationType.EMAIL,
                "Your lease ends in {{daysUntilExpiry}} days",
                "Heads-up — your lease is scheduled to end on {{endDate}} "
                        + "({{daysUntilExpiry}} days away).\n\n"
                        + "If you'd like to renew, open the Anirudh Homes app and tap *Renew lease* "
                        + "before then. If you're moving out, no action needed — your tenancy "
                        + "will close automatically and your security deposit refund will "
                        + "be initiated within 7 working days of move-out.",
                List.of("endDate", "daysUntilExpiry", "rentAmount"));
        seedIfAbsent("lease-expiry-sms", NotificationCategory.LEASE_EXPIRY,
                NotificationType.SMS, null,
                "Anirudh Homes: lease ends {{endDate}} ({{daysUntilExpiry}}d). Renew or move out in the app.",
                List.of("endDate", "daysUntilExpiry"));
        seedIfAbsent("lease-expiry-whatsapp", NotificationCategory.LEASE_EXPIRY,
                NotificationType.WHATSAPP, null,
                "📅 *Lease ending soon*\n\nYour lease ends on *{{endDate}}* "
                        + "({{daysUntilExpiry}} days from now).\n\n"
                        + "Want to stay? Tap *Renew lease* in the Anirudh Homes app.\n"
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
                        + "The updated lease PDF is available under *Documents* in the Anirudh Homes app.",
                List.of("previousEndDate", "newEndDate", "previousRent", "newRent"));
        seedIfAbsent("lease-renewed-sms", NotificationCategory.LEASE_RENEWED,
                NotificationType.SMS, null,
                "Anirudh Homes: lease renewed until {{newEndDate}}. New rent Rs.{{newRent}}/mo.",
                List.of("newEndDate", "newRent"));
        seedIfAbsent("lease-renewed-whatsapp", NotificationCategory.LEASE_RENEWED,
                NotificationType.WHATSAPP, null,
                "♻️ *Lease renewed*\n\nWelcome back for another term.\n\n"
                        + "*New end date:* {{newEndDate}}\n*New rent:* ₹{{newRent}}/month\n\n"
                        + "Download the updated lease PDF from *Documents* in the Anirudh Homes app.",
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
                        + "• Your final payment statement is now in *Documents* in the Anirudh Homes app.\n"
                        + "• Need anything? Reply to this email or message us in the app.\n\n"
                        + "Thanks for staying with Anirudh Homes.",
                List.of("terminatedOn", "terminationReason", "flatId"));
        seedIfAbsent("lease-terminated-sms", NotificationCategory.LEASE_TERMINATED,
                NotificationType.SMS, null,
                "Anirudh Homes: your tenancy ended {{terminatedOn}}. Deposit refund (if any) in 7 working days.",
                List.of("terminatedOn"));
        seedIfAbsent("lease-terminated-whatsapp", NotificationCategory.LEASE_TERMINATED,
                NotificationType.WHATSAPP, null,
                "👋 *Tenancy ended*\n\nYour tenancy ended on *{{terminatedOn}}* "
                        + "(_{{terminationReason}}_).\n\n"
                        + "If a security deposit was held, we'll refund it to your bank "
                        + "account on file within 7 working days. Your final statement "
                        + "is in *Documents* in the Anirudh Homes app.\n\nThanks for staying with us. 🙏",
                List.of("terminatedOn", "terminationReason"));

        /* ─── TENANT_VACATING_NOTICE — owner-facing 10-day-prior warning
         * (Issue #5). Fired by property-service's VacateScheduler when
         * a tenant's scheduledVacateDate lands within the next 10 days.
         * Variables: flatNumber, tenantName, vacateDate, daysUntilVacate.
         */
        seedIfAbsent("tenant-vacating-notice-email", NotificationCategory.TENANT_VACATING_NOTICE,
                NotificationType.EMAIL,
                "{{tenantName}} is vacating Flat {{flatNumber}} in {{daysUntilVacate}} days",
                "Hi,\n\nHeads-up — your tenant {{tenantName}} is scheduled to move out of "
                        + "Flat {{flatNumber}} on {{vacateDate}} ({{daysUntilVacate}} days from now).\n\n"
                        + "What to do now:\n"
                        + "• Plan a move-out walkthrough close to the date\n"
                        + "• Have the deposit-refund flow ready (7-day SLA from move-out)\n"
                        + "• List the flat again in the Anirudh Homes dashboard so it's discoverable "
                        + "on the day they move out\n\n"
                        + "You'll get another notification on the move-out day itself.",
                List.of("tenantName", "flatNumber", "vacateDate", "daysUntilVacate"));
        seedIfAbsent("tenant-vacating-notice-sms", NotificationCategory.TENANT_VACATING_NOTICE,
                NotificationType.SMS, null,
                "Anirudh Homes: tenant {{tenantName}} vacating Flat {{flatNumber}} on {{vacateDate}} ({{daysUntilVacate}}d). Plan walkthrough + re-listing.",
                List.of("tenantName", "flatNumber", "vacateDate", "daysUntilVacate"));
        seedIfAbsent("tenant-vacating-notice-whatsapp", NotificationCategory.TENANT_VACATING_NOTICE,
                NotificationType.WHATSAPP, null,
                "📅 *Tenant vacating soon*\n\n*{{tenantName}}* is moving out of *Flat {{flatNumber}}* on "
                        + "*{{vacateDate}}* (in {{daysUntilVacate}} days).\n\n"
                        + "Next steps in the Anirudh Homes app:\n"
                        + "• Schedule a walkthrough\n"
                        + "• Re-list the flat to keep it visible from day one\n"
                        + "• Confirm deposit refund details\n\n"
                        + "We'll ping again on the day itself.",
                List.of("tenantName", "flatNumber", "vacateDate", "daysUntilVacate"));

        /* ─── ADMIN_BROADCAST (Issue #9) ───
         * Admin-composed announcements — no template variables, the
         * service passes subjectOverride + messageOverride straight
         * through deliver(). We still seed a fallback template here so
         * the preferences UI can show "Admin broadcasts" as a mutable
         * category (PreferenceService scans the templates table to
         * build the muteable-category list).
         */
        seedIfAbsent("admin-broadcast-email", NotificationCategory.ADMIN_BROADCAST,
                NotificationType.EMAIL,
                "Anirudh Homes announcement",
                "Anirudh Homes team has a platform-wide announcement for you. "
                        + "Check the app for details.",
                List.of());
        seedIfAbsent("admin-broadcast-inapp", NotificationCategory.ADMIN_BROADCAST,
                NotificationType.INAPP,
                "Anirudh Homes announcement",
                "Anirudh Homes team has a platform-wide announcement for you.",
                List.of());

        /* ─── DOCUMENT_APPROVED / DOCUMENT_REJECTED (Issue #9) ───
         * Tenant-facing — confirms the owner's decision on a doc the
         * tenant uploaded. Variables: documentType, rejectionReason
         * (rejected variant only). All three channels seeded so the
         * tenant gets the same news on whichever channel they're on.
         */
        seedIfAbsent("document-approved-email", NotificationCategory.DOCUMENT_APPROVED,
                NotificationType.EMAIL,
                "Your {{documentType}} was approved",
                "Hi,\n\nYour owner has approved your {{documentType}} document. "
                        + "It's now on your tenant file and you don't need to do anything else.\n\n"
                        + "You can view all your documents under *Documents* in the Anirudh Homes app.",
                List.of("documentType"));
        seedIfAbsent("document-approved-sms", NotificationCategory.DOCUMENT_APPROVED,
                NotificationType.SMS, null,
                "Anirudh Homes: your {{documentType}} was approved by your owner. No action needed.",
                List.of("documentType"));
        seedIfAbsent("document-approved-whatsapp", NotificationCategory.DOCUMENT_APPROVED,
                NotificationType.WHATSAPP, null,
                "✅ *Document approved*\n\nYour *{{documentType}}* has been approved by your owner. "
                        + "It's filed on your tenant record — nothing more to do.",
                List.of("documentType"));

        seedIfAbsent("document-rejected-email", NotificationCategory.DOCUMENT_REJECTED,
                NotificationType.EMAIL,
                "Your {{documentType}} needs another look",
                "Hi,\n\nYour owner couldn't accept the {{documentType}} you uploaded. They wrote:\n\n"
                        + "  \"{{rejectionReason}}\"\n\n"
                        + "Please re-upload a corrected version under *Documents* in the Anirudh Homes app. "
                        + "Once you upload again, the owner will review the new copy.",
                List.of("documentType", "rejectionReason"));
        seedIfAbsent("document-rejected-sms", NotificationCategory.DOCUMENT_REJECTED,
                NotificationType.SMS, null,
                "Anirudh Homes: your {{documentType}} was rejected. Reason: {{rejectionReason}}. Re-upload via the app.",
                List.of("documentType", "rejectionReason"));
        seedIfAbsent("document-rejected-whatsapp", NotificationCategory.DOCUMENT_REJECTED,
                NotificationType.WHATSAPP, null,
                "⚠️ *Document needs another look*\n\nYour owner couldn't accept the *{{documentType}}* "
                        + "you uploaded:\n\n> {{rejectionReason}}\n\n"
                        + "Re-upload a corrected version under *Documents* in the Anirudh Homes app.",
                List.of("documentType", "rejectionReason"));

        /* ─── MAINTENANCE_ASSIGNED / MAINTENANCE_RESOLVED — completing
         * the channel set for the in-flight maintenance pipeline.
         * MaintenanceEventListener already uses fanOut for these, so
         * without SMS/WhatsApp templates the legs render generic copy.
         */
        seedIfAbsent("maintenance-assigned-sms", NotificationCategory.MAINTENANCE_ASSIGNED,
                NotificationType.SMS, null,
                "Anirudh Homes: ticket {{requestId}} assigned to {{assignedTo}}. They'll be in touch.",
                List.of("requestId", "assignedTo"));
        seedIfAbsent("maintenance-assigned-whatsapp", NotificationCategory.MAINTENANCE_ASSIGNED,
                NotificationType.WHATSAPP, null,
                "🛠️ *Maintenance update*\n\nTicket *{{requestId}}* is assigned to "
                        + "*{{assignedTo}}*. They'll reach out shortly — track or "
                        + "comment on it in the Anirudh Homes app under *Maintenance*.",
                List.of("requestId", "assignedTo"));
        seedIfAbsent("maintenance-resolved-whatsapp", NotificationCategory.MAINTENANCE_RESOLVED,
                NotificationType.WHATSAPP, null,
                "✅ *Resolved*\n\nGreat news — ticket *{{requestId}}* is resolved "
                        + "(turnaround {{resolutionTimeMinutes}} minutes).\n\n"
                        + "Reply in the Anirudh Homes app if anything's still off.",
                List.of("requestId", "resolutionTimeMinutes"));

        /* ─── COMPLAINT_ACKNOWLEDGED / COMPLAINT_RESOLVED — same
         * reasoning: MaintenanceEventListener fans these out across
         * all channels; templates filled in here.
         */
        seedIfAbsent("complaint-acknowledged-sms", NotificationCategory.COMPLAINT_ACKNOWLEDGED,
                NotificationType.SMS, null,
                "Anirudh Homes: complaint {{requestNumber}} is being handled by {{assignedTo}}.",
                List.of("requestNumber", "assignedTo"));
        seedIfAbsent("complaint-acknowledged-whatsapp", NotificationCategory.COMPLAINT_ACKNOWLEDGED,
                NotificationType.WHATSAPP, null,
                "🔔 *Complaint update*\n\nYour complaint *{{requestNumber}}* is now being "
                        + "worked on by *{{assignedTo}}*. You'll be pinged when it's resolved.",
                List.of("requestNumber", "assignedTo"));
        seedIfAbsent("complaint-resolved-sms", NotificationCategory.COMPLAINT_RESOLVED,
                NotificationType.SMS, null,
                "Anirudh Homes: complaint {{requestNumber}} resolved. Reply in the app to re-open within 7d.",
                List.of("requestNumber"));
        seedIfAbsent("complaint-resolved-whatsapp", NotificationCategory.COMPLAINT_RESOLVED,
                NotificationType.WHATSAPP, null,
                "✅ *Complaint resolved*\n\nYour complaint *{{requestNumber}}* is closed.\n\n"
                        + "Not happy with the outcome? Reply in the Anirudh Homes app within 7 days "
                        + "and we'll re-open it.",
                List.of("requestNumber"));
    }

    /**
     * Update an existing template row in place when its body still
     * embeds the legacy {@code {{flatId}}} placeholder, OR create a
     * fresh row if none exists. Used by the LEASE_WELCOME cleanup pass
     * — UPDATE is preferred over DELETE-then-INSERT because the latter
     * has a window where the row is briefly missing, during which a
     * concurrent {@code flat.occupied} event would render the generic
     * "You have a new LEASE_WELCOME notification" fallback copy.
     *
     * <p>Idempotent: rows already using {@code {{flatNumber}}} are
     * left alone, so admin edits via TemplateController survive.
     */
    private void refreshLeaseWelcomeIfStale(NotificationCategory category,
                                             NotificationType type,
                                             String name,
                                             String subject,
                                             String bodyTemplate,
                                             List<String> variables) {
        repo.findByCategoryAndType(category, type)
                .ifPresentOrElse(existing -> {
                    String body = existing.getBodyTemplate();
                    // Refresh when the body still embeds the legacy
                    // {{flatId}} placeholder OR lacks the new {{signInUrl}}
                    // CTA (Issue #7). Admin-edited templates that include
                    // a different sign-in URL via a custom placeholder
                    // stay untouched.
                    boolean stale = body == null
                            || body.contains("{{flatId}}")
                            || !body.contains("{{signInUrl}}");
                    if (stale) {
                        log.info("Refreshing stale {} / {} template in place (missing flatNumber/signInUrl placeholder)",
                                category, type);
                        existing.setSubject(subject);
                        existing.setBodyTemplate(bodyTemplate);
                        existing.setVariables(variables);
                        repo.save(existing);
                    }
                }, () -> {
                    // Row was missing entirely (e.g. the previous
                    // delete+re-seed left it nuked). Recreate it.
                    log.info("Re-creating missing {} / {} template", category, type);
                    repo.save(NotificationTemplate.builder()
                            .name(name)
                            .category(category)
                            .type(type)
                            .subject(subject)
                            .bodyTemplate(bodyTemplate)
                            .variables(variables)
                            .build());
                });
    }

    /**
     * Refresh USER_REGISTRATION templates in place when the body is
     * missing the {@code {{signInUrl}}} CTA introduced in Issue #7.
     * Same in-place strategy as {@link #refreshLeaseWelcomeIfStale} so
     * an event that lands during the refresh window can't fall through
     * to the generic fallback copy.
     */
    private void refreshUserRegistrationIfStale(NotificationCategory category,
                                                 NotificationType type,
                                                 String name,
                                                 String subject,
                                                 String bodyTemplate,
                                                 List<String> variables) {
        repo.findByCategoryAndType(category, type)
                .ifPresentOrElse(existing -> {
                    String body = existing.getBodyTemplate();
                    if (body == null || !body.contains("{{signInUrl}}")) {
                        log.info("Refreshing {} / {} template in place (adding signInUrl CTA)",
                                category, type);
                        existing.setSubject(subject);
                        existing.setBodyTemplate(bodyTemplate);
                        existing.setVariables(variables);
                        repo.save(existing);
                    }
                }, () -> {
                    log.info("Re-creating missing {} / {} template", category, type);
                    repo.save(NotificationTemplate.builder()
                            .name(name)
                            .category(category)
                            .type(type)
                            .subject(subject)
                            .bodyTemplate(bodyTemplate)
                            .variables(variables)
                            .build());
                });
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

    /* ──────────────────────────────────────────────────────────────
     *   HTML-shell upgrade machinery + per-category body builders.
     *   Added 2026-05 (templates-too-vague feedback). Each category's
     *   body is assembled from EmailTemplateBuilder + a category-
     *   specific content block — variables stay as Mustache tokens
     *   so the existing TemplateService rendering pipeline keeps
     *   working.
     *
     *   The category list is the same as the corresponding plain-text
     *   seedIfAbsent calls above — both run, so a fresh DB seeds the
     *   plain-text version once (idempotent), then this loop refreshes
     *   it in place to the HTML version. Net effect on a fresh boot:
     *   plain text gets inserted then immediately replaced. Cheap and
     *   keeps the migration path symmetric with re-deploys against an
     *   existing DB.
     * ────────────────────────────────────────────────────────────── */

    /**
     * Upgrade an existing email-channel template row to the HTML
     * shell, OR create a fresh row if none exists. Idempotent —
     * skips rows that already carry the HTML_TEMPLATE_MARKER
     * sentinel, so re-runs are no-ops and an admin who hand-edited
     * the template through the API (and didn't include the marker)
     * is left alone.
     */
    private void upgradeEmailToHtml(NotificationCategory category,
                                    String subject,
                                    String htmlBody) {
        repo.findByCategoryAndType(category, NotificationType.EMAIL)
                .ifPresentOrElse(existing -> {
                    String body = existing.getBodyTemplate();
                    if (body != null && body.contains(HTML_TEMPLATE_MARKER)) {
                        return; // already on the new HTML shell
                    }
                    log.info("Upgrading {} / EMAIL template to HTML shell", category);
                    existing.setSubject(subject);
                    existing.setBodyTemplate(htmlBody);
                    // Variables list left untouched — the HTML body
                    // uses the same Mustache placeholders as the
                    // legacy plain-text version it's replacing.
                    repo.save(existing);
                }, () -> {
                    // No legacy row at all — happens on a brand-new
                    // database that hasn't run the plain-text seed
                    // pass yet. We still want the HTML row in place
                    // before any event lands.
                    log.info("Inserting fresh {} / EMAIL HTML template", category);
                    repo.save(NotificationTemplate.builder()
                            .name(category.name().toLowerCase().replace("_", "-") + "-email-html")
                            .category(category)
                            .type(NotificationType.EMAIL)
                            .subject(subject)
                            .bodyTemplate(htmlBody)
                            .variables(List.of())
                            .build());
                });
    }

    /* ─────────── Per-category HTML body builders ─────────── */

    private static String buildWelcomeEmailHtml() {
        return EmailTemplateBuilder.build(
                "Welcome to Anirudh Homes — your account is ready.",
                "Welcome, {{userName}} 👋",
                "Your Anirudh Homes account (<strong>{{email}}</strong>) is live as a "
                        + "<strong>{{role}}</strong>.<br><br>"
                        + "Sign in to start browsing verified listings, manage rent payments, "
                        + "and raise maintenance tickets — all in one place. No brokerage. "
                        + "Direct from owners.",
                "Sign in to Anirudh Homes",
                "{{signInUrl}}",
                "— The Anirudh Homes team")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildPasswordResetEmailHtml() {
        return EmailTemplateBuilder.build(
                "Reset your password — link valid until {{expiresAt}}.",
                "Reset your password",
                "Hi {{userName}},<br><br>"
                        + "We received a request to reset the password on your "
                        + "Anirudh Homes account. Tap the button below to set a new one.<br><br>"
                        + "<span style=\"font-size:13px;color:#64748b;\">"
                        + "This link is valid until <strong>{{expiresAt}}</strong>. "
                        + "If you didn't request this, you can safely ignore this email — "
                        + "your current password is unchanged.</span>",
                "Reset password",
                "{{resetLink}}",
                "— The Anirudh Homes team")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildPaymentCreatedEmailHtml() {
        return EmailTemplateBuilder.build(
                "New rent invoice {{invoiceNumber}} for ₹{{amount}}, due {{dueDate}}.",
                "Your rent invoice is ready",
                "A new rent invoice has been generated.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Invoice</td>"
                        + "<td style=\"font-weight:600;\">{{invoiceNumber}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Amount</td>"
                        + "<td style=\"font-weight:600;\">₹{{amount}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Due date</td>"
                        + "<td style=\"font-weight:600;\">{{dueDate}}</td></tr>"
                        + "</table><br>"
                        + "Pay early via UPI to avoid late fees.",
                "Pay rent now",
                "{{paymentUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildPaymentReminderEmailHtml() {
        return EmailTemplateBuilder.build(
                "Rent due in {{daysUntilDue}} day(s) — pay early.",
                "Quick rent reminder",
                "Hi {{userName}},<br><br>"
                        + "Your rent of <strong>₹{{amount}}</strong> is due in "
                        + "<strong>{{daysUntilDue}} day(s)</strong>. Settle it now to avoid "
                        + "late fees.<br><br>"
                        + "It only takes a tap — UPI QR is right inside the app.",
                "Pay rent now",
                "{{paymentUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildPaymentOverdueEmailHtml() {
        return EmailTemplateBuilder.build(
                "Rent is {{daysOverdue}} day(s) overdue — pay now to stop the late fee growing.",
                "Your rent is overdue",
                "Your rent payment is now <strong>{{daysOverdue}} day(s) late</strong>.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Rent</td>"
                        + "<td style=\"font-weight:600;\">₹{{amount}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Late fee</td>"
                        + "<td style=\"font-weight:600;color:#dc2626;\">₹{{lateFee}}</td></tr>"
                        + "</table><br>"
                        + "Late fees continue to accrue daily until the payment is cleared. "
                        + "Please pay immediately.",
                "Pay now",
                "{{paymentUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildPaymentReceiptEmailHtml() {
        return EmailTemplateBuilder.build(
                "Payment received — ₹{{amount}} via {{method}}.",
                "Payment received — thanks!",
                "We've received your rent payment.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Amount</td>"
                        + "<td style=\"font-weight:600;\">₹{{amount}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Method</td>"
                        + "<td style=\"font-weight:600;\">{{method}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Transaction id</td>"
                        + "<td style=\"font-weight:600;font-family:monospace;\">{{transactionId}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Date</td>"
                        + "<td style=\"font-weight:600;\">{{paidDate}}</td></tr>"
                        + "</table><br>"
                        + "Your invoice PDF is available under <em>Payments</em>.",
                "Download invoice",
                "{{receiptUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    /**
     * Owner-side mirror of the payment-receipt email. Same data points
     * (amount, method, txn id, date) but framed for the landlord — "rent
     * landed" not "thanks for paying". Fires alongside the tenant receipt
     * via {@code PaymentEventListener.onCompleted}.
     */
    private static String buildPaymentReceivedOwnerEmailHtml() {
        return EmailTemplateBuilder.build(
                "Rent of ₹{{amount}} received via {{method}}.",
                "Rent received",
                "Good news — a rent payment just landed in your account.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Amount</td>"
                        + "<td style=\"font-weight:600;color:#16a34a;\">₹{{amount}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Method</td>"
                        + "<td style=\"font-weight:600;\">{{method}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Transaction id</td>"
                        + "<td style=\"font-weight:600;font-family:monospace;\">{{transactionId}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Date</td>"
                        + "<td style=\"font-weight:600;\">{{paidDate}}</td></tr>"
                        + "</table><br>"
                        + "Open the Anirudh Homes app to see the full payment history "
                        + "and download GST invoices for your records.",
                "View payments",
                "{{ownerPaymentsUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildMaintenanceCreatedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Maintenance request {{requestNumber}} opened — {{category}}, {{priority}} priority.",
                "We've received your maintenance request",
                "Your request <strong>{{requestNumber}}</strong> is open and queued.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Category</td>"
                        + "<td style=\"font-weight:600;\">{{category}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Priority</td>"
                        + "<td style=\"font-weight:600;\">{{priority}}</td></tr>"
                        + "</table><br>"
                        + "We'll keep you posted on every status change.",
                "Track this ticket",
                "{{ticketUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildMaintenanceAssignedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Ticket {{requestId}} assigned to {{assignedTo}}.",
                "Your ticket is in progress",
                "Maintenance request <strong>{{requestId}}</strong> has been assigned to "
                        + "<strong>{{assignedTo}}</strong>. They'll reach out shortly with next steps. "
                        + "Reply or add details inside the app to keep everything in one thread.",
                "View ticket",
                "{{ticketUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildMaintenanceResolvedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Ticket {{requestId}} resolved — happy to help!",
                "Resolved!",
                "Great news — your maintenance request <strong>{{requestId}}</strong> is closed "
                        + "(turnaround: {{resolutionTimeMinutes}} minutes).<br><br>"
                        + "If anything's still off, reply inside the app within 7 days and "
                        + "we'll re-open the ticket.",
                "Open Maintenance",
                "{{ticketUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildComplaintCreatedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Complaint {{requestNumber}} registered — a manager will respond soon.",
                "We've registered your complaint",
                "Your complaint <strong>{{requestNumber}}</strong> is now on file.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">About</td>"
                        + "<td style=\"font-weight:600;\">{{complaintCategory}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Priority</td>"
                        + "<td style=\"font-weight:600;\">{{priority}}</td></tr>"
                        + "</table><br>"
                        + "A property manager will review it and reply via the in-app thread. "
                        + "You can track status any time under <em>Complaints</em>.",
                "Open this complaint",
                "{{complaintUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildComplaintAcknowledgedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Complaint {{requestNumber}} acknowledged — being handled by {{assignedTo}}.",
                "Your complaint is being handled",
                "Complaint <strong>{{requestNumber}}</strong> is now being worked on by "
                        + "<strong>{{assignedTo}}</strong>. You'll be notified when there's "
                        + "progress; replies appear in the in-app thread.",
                "View thread",
                "{{complaintUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildComplaintResolvedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Complaint {{requestNumber}} resolved.",
                "Your complaint has been resolved",
                "Your complaint <strong>{{requestNumber}}</strong> is closed.<br><br>"
                        + "Not happy with the outcome? Reply in the thread within 7 days and "
                        + "we'll re-open it for another round.",
                "Open thread",
                "{{complaintUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildMaintenanceRaisedForOwnerEmailHtml() {
        // tenantName + flatNumber are optional — the listener tries to
        // populate them but the KafkaEvents DTO may not carry them on
        // legacy publishers. Mustache truthy sections hide the
        // "by {{tenantName}}" / "on Flat {{flatNumber}}" fragments when
        // empty so the body still reads naturally.
        return EmailTemplateBuilder.build(
                "A maintenance request was just raised on your property.",
                "New maintenance request on your property",
                "Heads-up — a maintenance ticket has been opened on your "
                        + "property{{#flatNumber}}, <strong>Flat {{flatNumber}}</strong>{{/flatNumber}}"
                        + "{{#tenantName}} by <strong>{{tenantName}}</strong>{{/tenantName}}.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Ticket</td>"
                        + "<td style=\"font-weight:600;\">{{requestNumber}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Category</td>"
                        + "<td style=\"font-weight:600;\">{{category}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Priority</td>"
                        + "<td style=\"font-weight:600;\">{{priority}}</td></tr>"
                        + "{{#title}}<tr><td style=\"color:#64748b;\">Title</td>"
                        + "<td style=\"font-weight:600;\">{{title}}</td></tr>{{/title}}"
                        + "</table><br>"
                        + "Open the ticket in your owner dashboard to assign a technician "
                        + "or message the tenant directly. Owners who respond within a few "
                        + "hours get noticeably higher review scores.",
                "Open ticket",
                "{{frontendBaseUrl}}/owner/maintenance",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildComplaintRaisedForOwnerEmailHtml() {
        return EmailTemplateBuilder.build(
                "A complaint was just filed on your property — respond within 24 hours.",
                "New complaint on your property",
                "A new complaint has been filed on your "
                        + "property{{#flatNumber}}, <strong>Flat {{flatNumber}}</strong>{{/flatNumber}}"
                        + "{{#tenantName}} by <strong>{{tenantName}}</strong>{{/tenantName}}. "
                        + "Tenants notice fast responses — replying within a few hours "
                        + "significantly improves your owner rating.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Complaint</td>"
                        + "<td style=\"font-weight:600;\">{{requestNumber}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">About</td>"
                        + "<td style=\"font-weight:600;\">{{complaintCategory}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Priority</td>"
                        + "<td style=\"font-weight:600;\">{{priority}}</td></tr>"
                        + "</table><br>"
                        + "Open the thread to acknowledge, reply, or resolve the complaint.",
                "Open complaint",
                "{{frontendBaseUrl}}/owner/complaints",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildReviewReceivedForOwnerEmailHtml() {
        return EmailTemplateBuilder.build(
                "A new review was just left on your property.",
                "New review on your property",
                "A <strong>{{rating}}-star review</strong> was just submitted on your "
                        + "property{{#flatNumber}}, <strong>Flat {{flatNumber}}</strong>{{/flatNumber}}"
                        + "{{#tenantName}} by <strong>{{tenantName}}</strong>{{/tenantName}}.<br><br>"
                        + "{{#comment}}<blockquote style=\"margin:0 0 16px;padding:14px 18px;"
                        + "background:#f1f5f9;border-left:4px solid #14b8a6;border-radius:6px;"
                        + "font-style:italic;color:#475569;\">"
                        + "&ldquo;{{comment}}&rdquo;"
                        + "</blockquote>{{/comment}}"
                        + "Reviews are public on your listing — replies help future tenants "
                        + "see how engaged you are. Open the review to respond.",
                "Read review",
                "{{frontendBaseUrl}}/owner/reviews",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildDocumentApprovedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Your {{documentType}} was approved — nothing more to do.",
                "Your {{documentType}} was approved ✅",
                "Your owner has approved the <strong>{{documentType}}</strong> you "
                        + "uploaded. It's filed on your tenant record — no further action "
                        + "needed.<br><br>"
                        + "You can review all your uploaded documents any time under "
                        + "<em>Documents</em> in the Anirudh Homes app.",
                "View my documents",
                "{{frontendBaseUrl}}/app/documents",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildDocumentRejectedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Your {{documentType}} needs another look — please re-upload.",
                "Please re-upload your {{documentType}}",
                "Your owner couldn't accept the <strong>{{documentType}}</strong> you uploaded. "
                        + "Here's why they rejected it:<br><br>"
                        + "{{#rejectionReason}}<blockquote style=\"margin:0 0 16px;"
                        + "padding:14px 18px;background:#fef2f2;border-left:4px solid #dc2626;"
                        + "border-radius:6px;color:#7f1d1d;\">"
                        + "&ldquo;{{rejectionReason}}&rdquo;"
                        + "</blockquote>{{/rejectionReason}}"
                        + "{{^rejectionReason}}<p style=\"margin:0 0 16px;color:#7f1d1d;\">"
                        + "<em>No specific reason was given. Reach out to the owner via the "
                        + "app if you'd like more detail.</em></p>{{/rejectionReason}}"
                        + "Re-upload a corrected copy under <em>Documents</em> in the app. "
                        + "The owner will review the new version automatically — no need to "
                        + "ping anyone.",
                "Re-upload now",
                "{{frontendBaseUrl}}/app/documents",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }

    private static String buildLeaseSignedEmailHtml() {
        return EmailTemplateBuilder.build(
                "Your lease {{leaseNumber}} is signed — welcome aboard.",
                "Your lease is signed 🏡",
                "Congratulations — your tenancy is officially on.<br><br>"
                        + "<table cellpadding=\"6\" style=\"font-size:14px;color:#334155;\">"
                        + "<tr><td style=\"color:#64748b;\">Lease number</td>"
                        + "<td style=\"font-weight:600;\">{{leaseNumber}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Starts</td>"
                        + "<td style=\"font-weight:600;\">{{startDate}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Ends</td>"
                        + "<td style=\"font-weight:600;\">{{endDate}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Monthly rent</td>"
                        + "<td style=\"font-weight:600;\">₹{{rentAmount}}</td></tr>"
                        + "<tr><td style=\"color:#64748b;\">Security deposit</td>"
                        + "<td style=\"font-weight:600;\">₹{{deposit}}</td></tr>"
                        + "</table><br>"
                        + "Download the signed PDF any time from <em>Documents</em>.",
                "View lease",
                "{{leaseUrl}}",
                "— Anirudh Homes")
                + HTML_TEMPLATE_MARKER;
    }
}
