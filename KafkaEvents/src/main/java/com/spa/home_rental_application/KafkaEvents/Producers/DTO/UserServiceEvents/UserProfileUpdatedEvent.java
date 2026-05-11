package com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Published by User Service when a profile is updated.
 *
 * <p>{@code userId} is the User-Service primary id (NOT the auth-tier
 * id). {@code authUserId} carries the auth-tier id explicitly so
 * downstream consumers keyed on the JWT subject (notification-service
 * preferences, KYC service, etc.) can resolve without a follow-up
 * lookup.
 *
 * <p>{@code email} / {@code phone} are populated when those fields are
 * touched by the update — used by notification-service to sync its
 * delivery preferences (so SMS / WhatsApp light up as soon as the
 * tenant fills in their phone on the profile page).
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileUpdatedEvent {
    private String eventType;
    private String userId;
    /** Auth-tier id (matches JWT subject + X-Auth-User-Id header). */
    private String authUserId;
    /** Current email after the update; null if unchanged or not set. */
    private String email;
    /** Current phone after the update; null if unchanged or not set. */
    private String phone;
    private String changes;
    private Instant timestamp;
}
