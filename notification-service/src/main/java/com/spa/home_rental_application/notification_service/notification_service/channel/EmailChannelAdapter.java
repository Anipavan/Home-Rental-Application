package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.NotificationProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * SMTP-backed email delivery via Spring's {@link JavaMailSender}.
 *
 * <p>Sends multipart messages with both an HTML and a plain-text body
 * so every mail client renders something readable. The HTML body is
 * wrapped in a branded template (emerald header bar + simple type
 * stack) when the stored {@code message} looks like raw text; pre-
 * rendered HTML (starts with {@code <}) passes through.
 *
 * <p>Registration is gated on {@code app.notification.delivery-enabled}
 * (default {@code true}). Flip to {@code false} to swap for
 * {@link NoopChannelAdapter}. The {@link JavaMailSender} dependency
 * is satisfied by Spring Boot's {@code MailSenderAutoConfiguration},
 * which always creates one because our {@code application.yaml}
 * defaults {@code spring.mail.host} to {@code smtp.gmail.com} — so
 * the auto-config condition always matches and constructor injection
 * always succeeds.
 *
 * <p><b>History:</b> this used to also have
 * {@code @ConditionalOnBean(JavaMailSender.class)} as a second gate,
 * but that's a known Spring Boot gotcha — {@code @ConditionalOnBean}
 * on user-defined {@code @Component} classes evaluates BEFORE
 * auto-configurations run, so the JavaMailSender bean wasn't yet in
 * the context when the condition checked, and the adapter silently
 * failed to register. The Spring Boot docs explicitly warn against
 * this pattern. Removed in favour of relying on constructor injection
 * (Spring will throw a loud "no bean found" error at startup if
 * {@code spring.mail.host} is somehow unset, which is much easier to
 * debug than the silent "fall through to noop" we used to have).
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "true", matchIfMissing = true)
public class EmailChannelAdapter implements NotificationChannelAdapter {

    private final JavaMailSender mailSender;
    private final NotificationProperties props;

    public EmailChannelAdapter(JavaMailSender mailSender, NotificationProperties props) {
        this.mailSender = mailSender;
        this.props = props;
        // Startup banner — tells the operator at a glance whether the
        // email channel is live. If you DON'T see this line in
        // notification-service startup logs, the channel didn't
        // register (no JavaMailSender bean → no spring.mail.host set
        // → no MAIL_USERNAME/MAIL_PASSWORD env vars).
        log.info(
                "\n" +
                "============================================================\n" +
                " EMAIL channel REGISTERED — JavaMailSender wired.\n" +
                "   fromEmail : {}\n" +
                "   fromName  : {}\n" +
                "   sender    : {}\n" +
                " If outgoing mail fails, check the MAIL_USERNAME /\n" +
                " MAIL_PASSWORD env vars and that the Gmail account has an\n" +
                " App Password (NOT a regular login password).\n" +
                "============================================================",
                props.getFromEmail(), props.getFromName(),
                mailSender.getClass().getSimpleName());
    }

    @Override
    public NotificationType type() { return NotificationType.EMAIL; }

    @Override
    public void send(NotificationLog n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new IllegalArgumentException("Email recipient is missing");
        }
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime, true /* multipart */, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(
                    props.getFromEmail(), props.getFromName(),
                    StandardCharsets.UTF_8.name()));
            helper.setTo(n.getRecipient());
            helper.setSubject(n.getSubject() == null ? "Notification" : n.getSubject());
            String body = n.getMessage() == null ? "" : n.getMessage();
            String plain = looksLikeHtml(body) ? htmlToText(body) : body;
            String html = looksLikeHtml(body) ? body : wrapInBrandTemplate(n.getSubject(), body);
            helper.setText(plain, html);
            mailSender.send(mime);
            log.info("Sent email to={} subject={}", n.getRecipient(), n.getSubject());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            // Wrap so the dispatcher's retry path sees a runtime
            // exception instead of a checked one.
            throw new RuntimeException("Failed to send email: " + ex.getMessage(), ex);
        }
    }

    private static boolean looksLikeHtml(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.startsWith("<") && t.contains(">");
    }

    /** Crude tag-strip for the text/plain leg of the multipart. */
    private static String htmlToText(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Branded HTML shell — emerald header bar, content card, footer.
     * Inline styles only so Outlook / Gmail / Apple Mail sanitisers
     * don't strip anything.
     */
    private static String wrapInBrandTemplate(String subject, String body) {
        String safeSubject = subject == null ? "" : escape(subject);
        String bodyHtml = escape(body).replace("\n", "<br/>");
        return "<!doctype html><html><body style=\"margin:0;padding:0;"
                + "background:#f3faf6;font-family:-apple-system,Segoe UI,Inter,sans-serif;color:#0f172a;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"560\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\""
                + " style=\"background:#ffffff;border-radius:14px;overflow:hidden;"
                + "box-shadow:0 6px 24px -8px rgba(16,132,92,0.18);\">"
                + "<tr><td style=\"background:linear-gradient(135deg,#10b981,#14b8a6 55%,#0284c7);"
                + "padding:18px 24px;color:#ffffff;font-weight:700;font-size:18px;letter-spacing:-0.01em;\">"
                + "Hearth"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 28px 24px;font-size:15px;line-height:1.55;\">"
                + (safeSubject.isBlank() ? ""
                    : "<h1 style=\"margin:0 0 14px;font-size:20px;font-weight:700;color:#0f172a;\">"
                       + safeSubject + "</h1>")
                + "<div style=\"color:#334155;\">" + bodyHtml + "</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:14px 28px 22px;font-size:11px;color:#64748b;border-top:1px solid #e2e8f0;\">"
                + "Sent automatically — no need to reply. © Hearth, Home Rentals."
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table></body></html>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
