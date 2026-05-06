package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.NotificationProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP-backed email delivery via Spring's {@link JavaMailSender}.
 * Activated by default; flip {@code app.notification.delivery-enabled=false}
 * to swap for the {@link NoopChannelAdapter}.
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
    }

    @Override
    public NotificationType type() { return NotificationType.EMAIL; }

    @Override
    public void send(NotificationLog n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new IllegalArgumentException("Email recipient is missing");
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.getFromName() + " <" + props.getFromEmail() + ">");
        msg.setTo(n.getRecipient());
        msg.setSubject(n.getSubject());
        msg.setText(n.getMessage());
        mailSender.send(msg);
        log.info("Sent email to={} subject={}", n.getRecipient(), n.getSubject());
    }
}
