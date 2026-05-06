package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.exception.TemplateNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock NotificationTemplateRepository repo;

    TemplateService service() { return new TemplateService(repo); }

    @Test
    void render_replacesPlaceholders() {
        String body = "Hi {{name}}, your invoice {{invoice}} for ₹{{amount}} is due {{date}}.";
        Map<String, Object> vars = Map.of(
                "name", "Alice", "invoice", "INV-001", "amount", "8500", "date", "2026-06-01");
        String out = service().render(body, vars);
        assertThat(out).isEqualTo("Hi Alice, your invoice INV-001 for ₹8500 is due 2026-06-01.");
    }

    @Test
    void render_missingVariable_leavesBracketedPlaceholder() {
        String body = "Hello {{name}}, code: {{code}}";
        Map<String, Object> vars = Map.of("name", "Alice");
        assertThat(service().render(body, vars)).isEqualTo("Hello Alice, code: [code]");
    }

    @Test
    void render_nullTemplate_returnsEmpty() {
        assertThat(service().render(null, Map.of())).isEmpty();
    }

    @Test
    void render_noVars_returnsTemplateUnchanged() {
        assertThat(service().render("plain text {{x}}", null)).isEqualTo("plain text {{x}}");
    }

    @Test
    void render_handlesSpecialCharsInValue() {
        // Make sure values containing $ etc. don't blow up Matcher.appendReplacement
        Map<String, Object> vars = new HashMap<>();
        vars.put("amt", "$1,000.00");
        assertThat(service().render("amount: {{amt}}", vars)).isEqualTo("amount: $1,000.00");
    }

    @Test
    void findOrThrow_missing_throws() {
        when(repo.findByCategoryAndType(NotificationCategory.PAYMENT_RECEIPT, NotificationType.EMAIL))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() ->
                service().findOrThrow(NotificationCategory.PAYMENT_RECEIPT, NotificationType.EMAIL))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void findOrThrow_present_returnsTemplate() {
        NotificationTemplate t = NotificationTemplate.builder()
                .category(NotificationCategory.PAYMENT_RECEIPT).type(NotificationType.EMAIL).build();
        when(repo.findByCategoryAndType(NotificationCategory.PAYMENT_RECEIPT, NotificationType.EMAIL))
                .thenReturn(Optional.of(t));
        assertThat(service().findOrThrow(NotificationCategory.PAYMENT_RECEIPT, NotificationType.EMAIL))
                .isSameAs(t);
    }
}
