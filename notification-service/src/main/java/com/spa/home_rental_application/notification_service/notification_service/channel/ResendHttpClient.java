package com.spa.home_rental_application.notification_service.notification_service.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTPS client for Resend's REST API at https://api.resend.com/emails.
 *
 * <p>Used instead of SMTP because DigitalOcean blocks outbound SMTP ports
 * (25, 465, 587, 2525) for new accounts. Port 443 (HTTPS) is universally
 * allowed.
 *
 * <p>Reads the same SPRING_MAIL_PASSWORD env var that the legacy SMTP
 * config used — that variable now holds the Resend API key
 * (format: re_xxxxxxxxxxxxxxxxxxxx).
 *
 * <p>Authentication, DKIM signing, and domain reputation are identical
 * to SMTP — same Resend account, same verified domain.
 */
@Component
public class ResendHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ResendHttpClient.class);
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${spring.mail.password:}")
    private String apiKey;

    @Value("${app.notification.from-email:support@anirudhhomes.in}")
    private String fromEmail;

    @Value("${app.notification.from-name:Anirudh Homes}")
    private String fromName;

    /**
     * Send an email via Resend's REST API.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param htmlBody rendered HTML body (templates done upstream)
     * @return true if Resend returned 2xx, false otherwise
     */
    public boolean sendEmail(String to, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured (SPRING_MAIL_PASSWORD empty). " +
                    "Email to {} dropped silently.", to);
            return false;
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "from", fromName + " <" + fromEmail + ">",
                    "to", List.of(to),
                    "subject", subject,
                    "html", htmlBody
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                log.info("Resend OK: to={} subject=\"{}\" responseId={}",
                        to, subject, resp.body());
                return true;
            }
            log.warn("Resend returned non-2xx: status={} to={} body={}",
                    code, to, resp.body());
            return false;
        } catch (Exception e) {
            log.error("Resend API call failed: to={} error={}",
                    to, e.getMessage(), e);
            return false;
        }
    }
}