package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.spa.home_rental_application.notification_service.notification_service.config.NotificationProperties;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP-backed email delivery via Spring's {@link JavaMailSender}.
 *
 * <p>Two conditions gate this bean's registration:
 * <ul>
 *   <li>{@code app.notification.delivery-enabled} (default {@code true}).
 *       Flip to {@code false} to swap for the {@link NoopChannelAdapter}
 *       deliberately.</li>
 *   <li>A {@link JavaMailSender} bean must already be in the context.
 *       Spring Boot's {@code MailSenderAutoConfiguration} only creates one
 *       when {@code spring.mail.host} is set. Without that, this adapter
 *       silently doesn't register — instead of bringing the whole service
 *       down with "no bean of type JavaMailSender". Anything that calls
 *       this adapter through {@code Optional<EmailChannelAdapter>} (the
 *       autoresponder, etc.) becomes a no-op; the dispatcher, which
 *       resolves channels by type, will route email-class notifications
 *       to {@link NoopChannelAdapter} instead.</li>
 * </ul>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.notification", name = "delivery-enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(JavaMailSender.class)
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
