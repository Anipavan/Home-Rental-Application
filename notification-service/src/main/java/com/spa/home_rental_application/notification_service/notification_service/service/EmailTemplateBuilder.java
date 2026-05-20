package com.spa.home_rental_application.notification_service.notification_service.service;

/**
 * Email body HTML builder. Produces a single branded shell with
 * inline CSS (the only CSS Gmail / Outlook / Apple Mail render
 * reliably across web + desktop + mobile clients).
 *
 * <p>Design goals:
 * <ul>
 *   <li><b>One CTA per email.</b> The big emerald button is the
 *       single thing the recipient should click — every flow has a
 *       specific destination (pay, view ticket, sign in).</li>
 *   <li><b>Mustache placeholders pass through.</b> The HTML the
 *       builder emits still contains {@code {{var}}} tokens; they
 *       resolve in TemplateService at delivery time. So a stored
 *       template can have variable subject/body/cta — same HTML
 *       shell, different content, no template-engine awareness here.</li>
 *   <li><b>Inline styles only.</b> No {@code <style>} block, no
 *       external CSS. Outlook for Windows in particular ignores
 *       most of CSS; inline styles are the only safe path.</li>
 *   <li><b>Plain-text fallback comes from Resend automatically.</b>
 *       Resend's API derives the text/plain alternative from the
 *       HTML so users on text-only clients still get a readable
 *       version without us hand-writing two bodies.</li>
 * </ul>
 *
 * <p>Tokens this shell expects (used by callers — none are mandatory):
 * <ul>
 *   <li>{@code subject} — large heading at the top of the card</li>
 *   <li>{@code body} — middle paragraph(s), can contain {@code <br>}</li>
 *   <li>{@code ctaUrl} — absolute URL for the primary action</li>
 *   <li>{@code ctaLabel} — button text ("Pay rent now", "View ticket", …)</li>
 *   <li>{@code preheader} — short hidden preview text (~80 chars)
 *       that mail clients show next to the subject in the inbox.</li>
 * </ul>
 */
public final class EmailTemplateBuilder {

    private EmailTemplateBuilder() {}

    /**
     * Returns a complete HTML document. Pass the already-rendered
     * subject (or a {@code {{subject}}}-style placeholder for it),
     * the body HTML, and the CTA. {@code ctaUrl} and {@code ctaLabel}
     * may be null/blank — the button block is omitted in that case
     * (useful for transactional notifications where the user just
     * needs to see the info, e.g. a payment receipt).
     *
     * @param preheader  hidden preview text shown next to the subject
     *                   in the inbox; keep ≤ 90 chars for Gmail
     * @param heading    bold large text inside the card (often the
     *                   same as the email subject)
     * @param bodyHtml   middle content; may include `<br>` and inline
     *                   `<a>` links
     * @param ctaLabel   button text; null/blank → no button rendered
     * @param ctaUrl     absolute URL the button points at
     * @param signOff    optional closing line (e.g. "— The Anirudh
     *                   Homes team"). null = no sign-off rendered.
     * @return ready-to-send HTML body
     */
    public static String build(String preheader,
                               String heading,
                               String bodyHtml,
                               String ctaLabel,
                               String ctaUrl,
                               String signOff) {
        String safePreheader = preheader == null ? "" : preheader;
        String safeHeading = heading == null ? "" : heading;
        String safeBody = bodyHtml == null ? "" : bodyHtml;
        String safeSignOff = signOff == null ? "" : signOff;

        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html>")
            .append("<html lang=\"en\">")
            .append("<head>")
            .append("<meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>").append(safeHeading).append("</title>")
            .append("</head>")
            .append("<body style=\"margin:0;padding:0;background:#f3faf6;")
            .append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Inter,Roboto,sans-serif;")
            .append("color:#0f172a;-webkit-font-smoothing:antialiased;\">")
            // Hidden preheader — Gmail / Apple Mail show this next to
            // the subject in the inbox. Helps the recipient decide
            // whether to open without giving away too much.
            .append("<div style=\"display:none;font-size:1px;color:#fefefe;line-height:1px;")
            .append("max-height:0px;max-width:0px;opacity:0;overflow:hidden;\">")
            .append(safePreheader)
            .append("</div>")
            .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" ")
            .append("style=\"background:#f3faf6;\">")
            .append("<tr><td align=\"center\" style=\"padding:32px 16px;\">")
            // ── Outer card ──
            .append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" ")
            .append("style=\"max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;")
            .append("box-shadow:0 8px 32px -12px rgba(15,118,110,0.22);\">")
            // ── Header / brand bar ──
            .append("<tr><td style=\"background:linear-gradient(135deg,#10b981 0%,#14b8a6 55%,#0284c7 100%);")
            .append("padding:24px 32px;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">")
            .append("<tr><td style=\"color:#ffffff;font-size:20px;font-weight:700;letter-spacing:-0.02em;\">")
            .append("Anirudh Homes")
            .append("</td>")
            .append("<td align=\"right\" style=\"color:rgba(255,255,255,0.85);font-size:12px;\">")
            .append("Verified rentals, direct from owners")
            .append("</td></tr></table>")
            .append("</td></tr>")
            // ── Body ──
            .append("<tr><td style=\"padding:36px 36px 8px;\">")
            .append("<h1 style=\"margin:0 0 18px;font-size:22px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;\">")
            .append(safeHeading)
            .append("</h1>")
            .append("<div style=\"font-size:15px;line-height:1.65;color:#334155;\">")
            .append(safeBody)
            .append("</div>")
            .append("</td></tr>");

        // ── CTA button ──
        // Rendered as a table-cell with inline styles — the only
        // markup Outlook for Windows respects for clickable rounded
        // buttons. The href and label both go through Mustache
        // substitution upstream, so callers can pass {{ctaUrl}}-style
        // placeholders.
        if (ctaLabel != null && !ctaLabel.isBlank()
                && ctaUrl != null && !ctaUrl.isBlank()) {
            html.append("<tr><td style=\"padding:8px 36px 32px;\">")
                .append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">")
                .append("<tr><td style=\"border-radius:10px;")
                .append("background:linear-gradient(135deg,#10b981,#0d9488);\">")
                .append("<a href=\"").append(ctaUrl).append("\" target=\"_blank\" ")
                .append("style=\"display:inline-block;padding:14px 26px;color:#ffffff;")
                .append("font-size:15px;font-weight:600;text-decoration:none;letter-spacing:0.01em;\">")
                .append(ctaLabel)
                .append("</a>")
                .append("</td></tr></table>")
                .append("<p style=\"margin:14px 0 0;font-size:12px;color:#64748b;\">")
                .append("Or paste this link into your browser:<br>")
                .append("<a href=\"").append(ctaUrl).append("\" target=\"_blank\" ")
                .append("style=\"color:#0d9488;text-decoration:underline;word-break:break-all;\">")
                .append(ctaUrl)
                .append("</a>")
                .append("</p>")
                .append("</td></tr>");
        }

        // ── Sign-off ──
        if (!safeSignOff.isBlank()) {
            html.append("<tr><td style=\"padding:0 36px 32px;font-size:14px;color:#475569;\">")
                .append(safeSignOff)
                .append("</td></tr>");
        }

        // ── Footer ──
        html.append("<tr><td style=\"padding:24px 36px;background:#f8fafc;border-top:1px solid #e2e8f0;")
            .append("font-size:11px;color:#64748b;line-height:1.6;\">")
            .append("This is a transactional message from Anirudh Homes — no need to reply.<br>")
            .append("Need help? Email <a href=\"mailto:support@anirudhhomes.in\" ")
            .append("style=\"color:#0d9488;text-decoration:none;\">support@anirudhhomes.in</a> ")
            .append("or WhatsApp <a href=\"https://wa.me/919108201223\" ")
            .append("style=\"color:#0d9488;text-decoration:none;\">+91 91082 01223</a>.<br>")
            .append("© Anirudh Homes · <a href=\"https://anirudhhomes.in\" ")
            .append("style=\"color:#64748b;text-decoration:underline;\">anirudhhomes.in</a>")
            .append("</td></tr>")
            .append("</table>")
            .append("</td></tr></table>")
            .append("</body></html>");

        return html.toString();
    }
}
