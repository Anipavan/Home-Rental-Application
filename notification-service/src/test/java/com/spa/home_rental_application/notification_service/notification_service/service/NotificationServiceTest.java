package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock TemplateService templateService;
    @Mock PreferenceService preferenceService;
    @Mock NotificationDispatcher dispatcher;
    @Mock NotificationLogRepository logRepo;

    NotificationService service() {
        return new NotificationService(templateService, preferenceService, dispatcher, logRepo);
    }

    @Test
    void rendersTemplate_andDispatches_whenChannelEnabled() {
        when(preferenceService.findOrDefault("U1")).thenReturn(
                UserNotificationPreference.builder().userId("U1").email("u1@x.com")
                        .emailEnabled(true).smsEnabled(true).pushEnabled(true)
                        .mutedCategories(new HashSet<>()).build());
        when(templateService.findOrThrow(NotificationCategory.PAYMENT_RECEIPT, NotificationType.EMAIL))
                .thenReturn(NotificationTemplate.builder()
                        .subject("Receipt {{amount}}").bodyTemplate("Paid {{amount}} via {{method}}")
                        .category(NotificationCategory.PAYMENT_RECEIPT).type(NotificationType.EMAIL)
                        .build());
        when(templateService.render("Receipt {{amount}}", Map.of("amount", "8500", "method", "UPI")))
                .thenReturn("Receipt 8500");
        when(templateService.render("Paid {{amount}} via {{method}}", Map.of("amount", "8500", "method", "UPI")))
                .thenReturn("Paid 8500 via UPI");
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog n = inv.getArgument(0); n.setId("N1"); return n;
        });
        when(dispatcher.dispatch(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service().sendFromTemplate("U1", NotificationType.EMAIL, NotificationCategory.PAYMENT_RECEIPT,
                Map.of("amount", "8500", "method", "UPI"));

        ArgumentCaptor<NotificationLog> dispatched = ArgumentCaptor.forClass(NotificationLog.class);
        verify(dispatcher).dispatch(dispatched.capture());
        NotificationLog n = dispatched.getValue();
        assertThat(n.getRecipient()).isEqualTo("u1@x.com");
        assertThat(n.getSubject()).isEqualTo("Receipt 8500");
        assertThat(n.getMessage()).isEqualTo("Paid 8500 via UPI");
    }

    @Test
    void skips_whenCategoryMuted() {
        Set<NotificationCategory> muted = Set.of(NotificationCategory.PAYMENT_REMINDER);
        when(preferenceService.findOrDefault("U1")).thenReturn(
                UserNotificationPreference.builder().userId("U1").email("u1@x.com")
                        .emailEnabled(true).mutedCategories(muted).build());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog n = inv.getArgument(0); n.setId("N1"); return n;
        });

        service().sendFromTemplate("U1", NotificationType.EMAIL, NotificationCategory.PAYMENT_REMINDER, Map.of());

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        verifyNoInteractions(dispatcher);
    }

    @Test
    void skips_whenChannelDisabled() {
        when(preferenceService.findOrDefault("U1")).thenReturn(
                UserNotificationPreference.builder().userId("U1").email("u1@x.com")
                        .emailEnabled(false)   // ← disabled
                        .smsEnabled(true).pushEnabled(true)
                        .mutedCategories(new HashSet<>()).build());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog n = inv.getArgument(0); n.setId("N1"); return n;
        });

        service().sendFromTemplate("U1", NotificationType.EMAIL, NotificationCategory.PAYMENT_RECEIPT, Map.of());

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        verifyNoInteractions(dispatcher);
    }

    @Test
    void marksFailed_whenRecipientMissing() {
        when(preferenceService.findOrDefault("U1")).thenReturn(
                UserNotificationPreference.builder().userId("U1")
                        .email(null)            // ← missing
                        .emailEnabled(true).smsEnabled(true).pushEnabled(true)
                        .mutedCategories(new HashSet<>()).build());
        when(templateService.findOrThrow(any(), any())).thenReturn(
                NotificationTemplate.builder().subject("s").bodyTemplate("b").build());
        when(templateService.render(any(), any())).thenReturn("rendered");
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog n = inv.getArgument(0); n.setId("N1"); return n;
        });

        service().sendFromTemplate("U1", NotificationType.EMAIL, NotificationCategory.PAYMENT_RECEIPT, Map.of());

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        verifyNoInteractions(dispatcher);
    }
}
