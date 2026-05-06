package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.PreferenceRequest;
import com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.repository.UserNotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    @Mock UserNotificationPreferenceRepository repo;

    PreferenceService service() { return new PreferenceService(repo); }

    @Test
    void findOrDefault_unknownUser_returnsAllChannelsEnabled() {
        when(repo.findByUserId("USR-1")).thenReturn(Optional.empty());
        UserNotificationPreference p = service().findOrDefault("USR-1");
        assertThat(p.isEmailEnabled()).isTrue();
        assertThat(p.isSmsEnabled()).isTrue();
        assertThat(p.isPushEnabled()).isTrue();
        assertThat(p.getMutedCategories()).isEmpty();
    }

    @Test
    void upsert_createsNewWhenAbsent() {
        when(repo.findByUserId("USR-1")).thenReturn(Optional.empty());
        when(repo.save(any(UserNotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new PreferenceRequest("u@x.com", "+919876543210", "tok",
                true, false, true, Set.of(NotificationCategory.PAYMENT_REMINDER));
        var resp = service().upsert("USR-1", req);

        assertThat(resp.email()).isEqualTo("u@x.com");
        assertThat(resp.smsEnabled()).isFalse();
        assertThat(resp.mutedCategories()).contains(NotificationCategory.PAYMENT_REMINDER);
    }

    @Test
    void upsert_updatesExisting_partial() {
        UserNotificationPreference existing = UserNotificationPreference.builder()
                .userId("USR-1").email("old@x.com").emailEnabled(true).smsEnabled(true).build();
        when(repo.findByUserId("USR-1")).thenReturn(Optional.of(existing));
        when(repo.save(any(UserNotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only set sms-disabled; everything else null → leaves existing value
        var req = new PreferenceRequest(null, null, null, null, false, null, null);
        var resp = service().upsert("USR-1", req);

        assertThat(resp.email()).isEqualTo("old@x.com");  // unchanged
        assertThat(resp.smsEnabled()).isFalse();          // updated
    }
}
