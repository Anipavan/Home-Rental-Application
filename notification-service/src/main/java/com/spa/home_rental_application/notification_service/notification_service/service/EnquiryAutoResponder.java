package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.channel.EmailChannelAdapter;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.entities.SupportTicket;
import com.spa.home_rental_application.notification_service.notification_service.entities.VisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Sends a "we got your message" email to the visitor as soon as a
 * support ticket or a visit request is created.
 *
 * <p>Sits between the create-flow services ({@link SupportTicketService},
 * {@link VisitRequestService}) and the existing
 * {@link EmailChannelAdapter}. We call the adapter directly rather than
 * routing through {@link NotificationService#send} because:
 * <ul>
 *   <li>The submitter may be a {@code "PUBLIC_VISITOR"} — there's no
 *       real userId to look up preferences against, and we don't want
 *       to pollute the {@code user_notification_preferences} collection
 *       with synthetic rows.</li>
 *   <li>Templates aren't worth the indirection for a two-line
 *       confirmation email.</li>
 *   <li>Failures here are deliberately silent — confirmation email is
 *       a nicety, not a contract; we never want a flaky SMTP outage to
 *       fail the parent ticket-create or visit-request-create
 *       transaction.</li>
 * </ul>
 *
 * <p>The {@link EmailChannelAdapter} bean is conditional on
 * {@code app.notification.delivery-enabled} — disabled in dev, enabled
 * in prod. We inject an {@link Optional} so the class is a no-op in dev
 * environments without hand-coding env checks.
 */
@Service
@Slf4j
public class EnquiryAutoResponder {

    private static final DateTimeFormatter PRETTY =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a")
                    .withZone(java.time.ZoneId.systemDefault());

    private final Optional<EmailChannelAdapter> emailAdapter;

    public EnquiryAutoResponder(Optional<EmailChannelAdapter> emailAdapter) {
        this.emailAdapter = emailAdapter;
    }

    /** Confirm receipt of a property enquiry / support ticket. */
    public void onSupportTicketCreated(SupportTicket ticket) {
        if (ticket == null) return;
        sendQuietly(
                ticket.getUserEmail(),
                "We received your message — " + safe(ticket.getSubject()),
                """
                Hi %s,

                Thanks for reaching out to Anirudh Homes. We've received your message and our \
                team will get back to you within 24 hours.

                Your message:
                %s

                If your need is urgent, you can also reach us on WhatsApp at \
                +91 99999 99999 or reply to this email.

                — The Anirudh Homes team
                """.formatted(
                        safeName(ticket.getUserName()),
                        safe(ticket.getMessage())
                )
        );
    }

    /** Confirm receipt of a visit request and quote back the slot. */
    public void onVisitRequestCreated(VisitRequest request) {
        if (request == null) return;
        String slot = request.getPreferredAt() == null
                ? "your preferred time"
                : PRETTY.format(request.getPreferredAt());
        String property = safe(request.getPropertyLabel());

        sendQuietly(
                request.getVisitorEmail(),
                "Visit request received — " + property,
                """
                Hi %s,

                Thanks for booking a visit to %s. We've passed your request to the \
                owner and they'll confirm your slot shortly.

                Requested time:  %s
                Phone on file:   %s

                If anything changes, just reply to this email.

                — The Anirudh Homes team
                """.formatted(
                        safeName(request.getVisitorName()),
                        property,
                        slot,
                        safe(request.getVisitorPhone())
                )
        );
    }

    /* -------------------- internal -------------------- */

    private void sendQuietly(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.debug("Auto-responder skipped: no recipient email");
            return;
        }
        if (emailAdapter.isEmpty()) {
            log.debug("Auto-responder skipped: email delivery disabled "
                    + "(app.notification.delivery-enabled=false)");
            return;
        }
        try {
            NotificationLog one = NotificationLog.builder()
                    .userId("AUTORESPONDER")
                    .type(NotificationType.EMAIL)
                    .category(NotificationCategory.GENERIC)
                    .recipient(to)
                    .subject(subject)
                    .message(body)
                    .status(NotificationStatus.PENDING)
                    .sentAt(Instant.now())
                    .build();
            emailAdapter.get().send(one);
        } catch (Exception ex) {
            // Non-fatal — log and move on so the parent transaction commits.
            log.warn("Auto-responder email to={} failed: {}", to, ex.getMessage());
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "there";
        return name.trim();
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
    }
}
