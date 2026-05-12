package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.DTO.NotificationMapper;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.PreferenceRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.PreferenceResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference;
import com.spa.home_rental_application.notification_service.notification_service.repository.UserNotificationPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;

/**
 * Read/upsert user notification preferences. The default for any
 * user without an explicit row is "all channels enabled, nothing muted".
 */
@Service
@Slf4j
public class PreferenceService {

    private final UserNotificationPreferenceRepository repo;

    public PreferenceService(UserNotificationPreferenceRepository repo) {
        this.repo = repo;
    }

    public UserNotificationPreference findOrDefault(String userId) {
        return repo.findByUserId(userId).orElseGet(() ->
                UserNotificationPreference.builder()
                        .userId(userId)
                        .emailEnabled(true).smsEnabled(true).pushEnabled(true)
                        // WhatsApp off by default — explicit opt-in via
                        // the preferences UI. Many users prefer SMS for
                        // transactional pings and reserve WhatsApp for
                        // richer conversations.
                        .whatsappEnabled(false)
                        // In-app on by default — backs the notification bell
                        // and never needs an external recipient.
                        .inappEnabled(true)
                        .mutedCategories(new HashSet<>())
                        .build());
    }

    public PreferenceResponse get(String userId) {
        return NotificationMapper.toResponse(findOrDefault(userId));
    }

    /**
     * Audit M21: tolerant phone validator. Accepts E.164 (+91…),
     * 10-digit Indian local, and standard hyphen/space-separated
     * formats. Empty / null is allowed (user opted out of phone).
     * Twilio adapters get a final-mile pass at dispatch time too,
     * but stopping the malformed value at the persistence boundary
     * gives the user immediate feedback.
     */
    private static final java.util.regex.Pattern PHONE_REGEX =
            java.util.regex.Pattern.compile("^\\+?[0-9][0-9\\s\\-]{8,18}[0-9]$");

    public PreferenceResponse upsert(String userId, PreferenceRequest dto) {
        // M21: validate phone shape before we persist it. Saving a
        // malformed number wastes an SMS / WhatsApp send + masks the
        // problem until the Twilio attempt fails async.
        if (dto.phone() != null && !dto.phone().isBlank()
                && !PHONE_REGEX.matcher(dto.phone().trim()).matches()) {
            throw new IllegalArgumentException(
                    "Phone number is malformed. Use 10 digits, with country code if international (e.g. +91-9876543210).");
        }

        UserNotificationPreference existing = repo.findByUserId(userId).orElseGet(() ->
                UserNotificationPreference.builder().userId(userId).build());
        if (dto.email() != null)             existing.setEmail(dto.email());
        if (dto.phone() != null)             existing.setPhone(dto.phone().trim());
        if (dto.deviceToken() != null)       existing.setDeviceToken(dto.deviceToken());
        if (dto.emailEnabled() != null)      existing.setEmailEnabled(dto.emailEnabled());
        if (dto.smsEnabled() != null)        existing.setSmsEnabled(dto.smsEnabled());
        if (dto.whatsappEnabled() != null)   existing.setWhatsappEnabled(dto.whatsappEnabled());
        if (dto.pushEnabled() != null)       existing.setPushEnabled(dto.pushEnabled());
        if (dto.mutedCategories() != null)   existing.setMutedCategories(dto.mutedCategories());
        UserNotificationPreference saved = repo.save(existing);
        log.info("Preferences upserted user={} email={} sms={} whatsapp={} push={}",
                userId, saved.isEmailEnabled(), saved.isSmsEnabled(),
                saved.isWhatsappEnabled(), saved.isPushEnabled());
        return NotificationMapper.toResponse(saved);
    }
}
