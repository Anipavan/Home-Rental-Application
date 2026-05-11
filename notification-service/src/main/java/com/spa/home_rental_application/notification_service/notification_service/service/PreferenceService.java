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

    public PreferenceResponse upsert(String userId, PreferenceRequest dto) {
        UserNotificationPreference existing = repo.findByUserId(userId).orElseGet(() ->
                UserNotificationPreference.builder().userId(userId).build());
        if (dto.email() != null)             existing.setEmail(dto.email());
        if (dto.phone() != null)             existing.setPhone(dto.phone());
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
