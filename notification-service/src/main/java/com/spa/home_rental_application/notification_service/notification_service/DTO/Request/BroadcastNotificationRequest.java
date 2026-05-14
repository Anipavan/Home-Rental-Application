package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Issue #9 — body for {@code POST /notifications/admin/broadcast}.
 *
 * <p>Admin types a {@link #subject} and {@link #message}; the SPA
 * resolves the target audience (every TENANT, every OWNER, or
 * everyone) on the client side and ships the resulting list of auth
 * user IDs in {@link #userIds}. We deliberately keep the audience
 * resolution on the client because the auth-service already exposes
 * the {@code /auth/role/{role}} endpoint the admin UI uses to render
 * its user table — no need to duplicate that lookup behind a Feign
 * client here.
 *
 * <p>Subject + message render as-is (no template). The {@code <}, {@code >}
 * and {@code &} characters get HTML-escaped before the EMAIL body is
 * sent (per {@link com.spa.home_rental_application.notification_service.notification_service.service.NotificationService}'s
 * delivery path), so admins can write Markdown-ish copy without
 * worrying about HTML injection.
 */
public record BroadcastNotificationRequest(
        /** Resolved auth user IDs to deliver the announcement to. */
        @NotEmpty(message = "Pick at least one recipient.")
        @Size(max = 10_000, message = "Broadcast can target at most 10,000 recipients in a single call.")
        List<String> userIds,

        @NotBlank(message = "Subject is mandatory.")
        @Size(max = 200, message = "Subject must be 200 characters or fewer.")
        String subject,

        @NotBlank(message = "Message is mandatory.")
        @Size(max = 4000, message = "Message must be 4000 characters or fewer.")
        String message
) {}
