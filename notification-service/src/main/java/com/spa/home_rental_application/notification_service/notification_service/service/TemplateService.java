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
 * against the supplied variables. Placeholder syntax: {@code {{var}}}.
 * Missing variables fall back to {@code [var]} so problems show up in
 * the rendered text rather than corrupting the message silently.
 */
@Service
@Slf4j
public class TemplateService {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}");

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
        if (template == null) return "";
        if (vars == null || vars.isEmpty()) return template;
        Matcher m = VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object value = vars.get(key);
            String replacement = value == null ? "[" + key + "]" : Matcher.quoteReplacement(value.toString());
            m.appendReplacement(out, replacement);
        }
        m.appendTail(out);
        return out.toString();
    }
}
