package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.channel.NotificationChannelAdapter;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock NotificationLogRepository logRepo;

    static class StubAdapter implements NotificationChannelAdapter {
        final NotificationType t;
        boolean fail = false;
        Exception thrown = new RuntimeException("smtp down");
        StubAdapter(NotificationType t) { this.t = t; }
        @Override public NotificationType type() { return t; }
        @Override public void send(NotificationLog log) throws Exception {
            if (fail) throw thrown;
        }
    }

    @Test
    void dispatch_success_marksSentAndStampsTimestamp() {
        StubAdapter email = new StubAdapter(NotificationType.EMAIL);
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));
        NotificationDispatcher d = new NotificationDispatcher(List.of(email), logRepo);

        NotificationLog n = NotificationLog.builder().id("N1").type(NotificationType.EMAIL).build();
        NotificationLog out = d.dispatch(n);

        assertThat(out.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(out.getSentAt()).isNotNull();
        assertThat(out.getErrorMessage()).isNull();
    }

    @Test
    void dispatch_failure_marksFailed_andIncrementsRetry() {
        StubAdapter email = new StubAdapter(NotificationType.EMAIL);
        email.fail = true;
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));
        NotificationDispatcher d = new NotificationDispatcher(List.of(email), logRepo);

        NotificationLog n = NotificationLog.builder()
                .id("N1").type(NotificationType.EMAIL).retryCount(0).build();
        NotificationLog out = d.dispatch(n);

        assertThat(out.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(out.getErrorMessage()).isEqualTo("smtp down");
        assertThat(out.getRetryCount()).isEqualTo(1);
    }

    @Test
    void dispatch_unknownChannel_marksFailed() {
        // Only Email adapter registered; ask for SMS
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));
        NotificationDispatcher d = new NotificationDispatcher(
                List.of(new StubAdapter(NotificationType.EMAIL)), logRepo);

        NotificationLog n = NotificationLog.builder()
                .id("N1").type(NotificationType.SMS).build();
        NotificationLog out = d.dispatch(n);

        assertThat(out.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(out.getErrorMessage()).contains("No adapter registered");
    }
}
