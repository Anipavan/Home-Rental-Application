package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.exception.TemplateNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up the template for a (category, type) tuple and renders it
 * against the supplied variables.
 *
 * <h2>Template syntax</h2>
 * <ul>
 *   <li>{@code {{var}}} — replaced with {@code vars.get("var")}. Missing
 *       keys render as {@code [var]} so problems surface in the message
 *       rather than corrupting it silently.</li>
 *   <li>{@code {{#var}}...{{/var}}} — Mustache-style truthy section.
 *       The inner content renders only when the variable is present
 *       AND non-empty / non-false. Lets templates conditionally
 *       include text — e.g. "available {{#preferredAt}} on
 *       {{preferredAt}}{{/preferredAt}}" omits the entire " on …"
 *       fragment when no preferred time was specified.</li>
 *   <li>{@code {{^var}}...{{/var}}} — inverse section. Renders only
 *       when the variable is absent / empty / false. Useful for
 *       fallback copy: "{{^visitorName}}Someone{{/visitorName}}
 *       wants to see the flat".</li>
 * </ul>
 *
 * <p>Sections are processed BEFORE variables so a section's body can
 * itself contain {@code {{var}}} placeholders that resolve in the
 * outer render pass.
 */
@Service
@Slf4j
public class TemplateService {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}");

    /**
     * Mustache truthy / inverse section. Group(1) = `#` or `^`,
     * group(2) = variable name, group(3) = inner body. DOTALL so
     * multiline templates work; reluctant `.*?` so adjacent sections
     * don't merge into one match.
     */
    private static final Pattern SECTION = Pattern.compile(
            "\\{\\{([#^])\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}(.*?)\\{\\{/\\s*\\2\\s*\\}\\}",
            Pattern.DOTALL);

    private final NotificationTemplateRepository repo;

    public TemplateService(NotificationTemplateRepository repo) {
        this.repo = repo;
    }

    public NotificationTemplate findOrThrow(NotificationCategory category, NotificationType type) {
        return repo.findByCategoryAndType(category, type).orElseThrow(
                () -> new TemplateNotFoundException(
                        "No template for category=" + category + " type=" + type));
    }

    public String render(String template, Map<String, Object> vars) {
        return render(template, vars, false);
    }

    /**
     * Audit M19: HTML-escape variable substitutions when the template
     * is being rendered for the EMAIL channel. SMS / WhatsApp / INAPP
     * still get raw values (those channels render as plain text;
     * escaping there would surface visible {@code &amp;amp;} sequences).
     *
     * <p>Without escaping, a template variable carrying user input
     * (e.g. a complaint description) could inject {@code &lt;script&gt;}
     * into the email HTML. Outlook + Gmail clients sandbox script
     * execution, but other clients (and the in-app email preview)
     * don't necessarily — and an enterprising attacker could still
     * use it for credible-looking phishing.
     */
    public String render(String template, Map<String, Object> vars, boolean htmlEscape) {
        if (template == null) return "";
        if (vars == null) vars = Map.of();
        // Pass 1: resolve sections. Iterate until no more matches so
        // nested sections collapse correctly.
        String working = template;
        for (int safety = 0; safety < 8; safety++) {
            Matcher sm = SECTION.matcher(working);
            if (!sm.find()) break;
            StringBuilder rebuilt = new StringBuilder();
            sm.reset();
            while (sm.find()) {
                String kind = sm.group(1);   // # or ^
                String key  = sm.group(2);
                String body = sm.group(3);
                boolean truthy = isTruthy(vars.get(key));
                boolean keep = ("#".equals(kind) && truthy) || ("^".equals(kind) && !truthy);
                sm.appendReplacement(rebuilt, Matcher.quoteReplacement(keep ? body : ""));
            }
            sm.appendTail(rebuilt);
            working = rebuilt.toString();
        }

        // Pass 2: resolve plain {{var}} placeholders.
        if (vars.isEmpty()) return working;
        Matcher m = VAR.matcher(working);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object value = vars.get(key);
            String rendered = value == null ? "[" + key + "]" : value.toString();
            if (htmlEscape) rendered = escapeHtml(rendered);
            m.appendReplacement(out, Matcher.quoteReplacement(rendered));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Minimal HTML escape — covers the five reserved chars
     * (&amp; &lt; &gt; &quot; &#39;). Avoids the full Apache Commons
     * dep for a five-line escape that's been correct since HTML 4.01.
     */
    private static String escapeHtml(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> b.append("&amp;");
                case '<'  -> b.append("&lt;");
                case '>'  -> b.append("&gt;");
                case '"'  -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default   -> b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Mustache truthiness:
     *  - null → false
     *  - empty string → false
     *  - Boolean false → false
     *  - empty collection / map → false
     *  - everything else → true
     */
    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof CharSequence cs) return !cs.toString().isBlank();
        if (v instanceof java.util.Collection<?> c) return !c.isEmpty();
        if (v instanceof java.util.Map<?, ?> mp) return !mp.isEmpty();
        return true;
    }
}
