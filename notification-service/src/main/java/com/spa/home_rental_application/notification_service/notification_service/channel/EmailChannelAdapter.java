package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.NotificationProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HTTPS-backed email delivery via Resend's REST API at
 * {@code https://api.resend.com/emails}.
 *
 * <p><b>Why HTTPS instead of SMTP?</b> DigitalOcean blocks all outbound
 * SMTP ports (25, 465, 587, 2525) by policy for new accounts (confirmed
 * via support ticket #12223180). Port 443 (HTTPS) is universally
 * allowed. Same Resend account, same verified domain, same DKIM/SPF/
 * DMARC infrastructure — just different transport.
 *
 * <p>Authentication: reads the API key from {@code spring.mail.password}
 * (which used to hold the SMTP password — now holds the Resend
 * {@code re_*} key). Reusing the variable keeps the deploy contract
 * unchanged: same env var, same .env, same secret rotation procedure.
 *
 * <p>Branding: HTML body is wrapped in our emerald header / content card
 * shell when the stored {@code message} looks like raw text. Pre-rendered
 * HTML (starts with {@code <}) passes through as-is.
 *
 * <p>Registration is gated on {@code app.notification.delivery-enabled}
 * (default {@code true}). Flip to {@code false} to swap for
 * {@link NoopChannelAdapter}.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "true", matchIfMissing = true)
public class EmailChannelAdapter implements NotificationChannelAdapter {

    private final ResendHttpClient resendClient;
    private final NotificationProperties props;

    public EmailChannelAdapter(ResendHttpClient resendClient, NotificationProperties props) {
        this.resendClient = resendClient;
        this.props = props;
        // Startup banner — tells the operator at a glance whether the
        // email channel is live. If you DON'T see this line in
        // notification-service startup logs, the channel didn't
        // register (delivery-enabled=false in application.yaml).
        log.info(
                "\n" +
                        "============================================================\n" +
                        " EMAIL channel REGISTERED — Resend HTTPS API wired.\n" +
                        "   fromEmail : {}\n" +
                        "   fromName  : {}\n" +
                        "   endpoint  : https://api.resend.com/emails (port 443)\n" +
                        " Outbound SMTP (587/465/2525) is blocked by DigitalOcean.\n" +
                        " Using Resend REST API on port 443 — same API key in\n" +
                        " SPRING_MAIL_PASSWORD env var, same domain DKIM/SPF/DMARC.\n" +
                        "============================================================",
                props.getFromEmail(), props.getFromName());
    }

    @Override
    public NotificationType type() { return NotificationType.EMAIL; }

    @Override
    public void send(NotificationLog n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new IllegalArgumentException("Email recipient is missing");
        }
        String subject = n.getSubject() == null ? "Notification" : n.getSubject();
        String body = n.getMessage() == null ? "" : n.getMessage();
        // Same branding logic as the old SMTP path: raw text gets wrapped
        // in the emerald header shell; pre-rendered HTML passes through.
        // Resend auto-generates the text/plain alternative from the HTML
        // so we no longer need to render both bodies ourselves.
        String html = looksLikeHtml(body) ? body : wrapInBrandTemplate(n.getSubject(), body);

        boolean delivered = resendClient.sendEmail(n.getRecipient(), subject, html);
        if (!delivered) {
            // Throw so the dispatcher's retry path picks it up. The
            // underlying cause (4xx response, network timeout, etc.) was
            // already logged at WARN/ERROR by ResendHttpClient.
            throw new RuntimeException(
                    "Failed to send email via Resend to " + n.getRecipient());
        }
        log.info("Sent email to={} subject={}", n.getRecipient(), subject);
    }

    private static boolean looksLikeHtml(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.startsWith("<") && t.contains(">");
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
                + "Anirudh Homes"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 28px 24px;font-size:15px;line-height:1.55;\">"
                + (safeSubject.isBlank() ? ""
                : "<h1 style=\"margin:0 0 14px;font-size:20px;font-weight:700;color:#0f172a;\">"
                + safeSubject + "</h1>")
                + "<div style=\"color:#334155;\">" + bodyHtml + "</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:14px 28px 22px;font-size:11px;color:#64748b;border-top:1px solid #e2e8f0;\">"
                + "Sent automatically — no need to reply. © Anirudh Homes."
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