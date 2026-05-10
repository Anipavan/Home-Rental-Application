package com.spa.home_rental_application.user_service.user_service.DTO.Response;

import java.time.LocalDate;

/**
 * Flat projection of a user joined with their auth-service identity.
 *
 * <p>{@code authUserId} is the auth-service primary id (a String of the
 * Long pk). Several places in the platform — {@code Flat.tenantId},
 * {@code Building.ownerId}, JWT subject, frontend {@code authUserId} —
 * key off this value, so any UI that lets an actor pick a user by role
 * (e.g. the owner-side "Assign tenant" dialog) needs it on the wire.
 *
 * <p>{@code id} is the user-service primary uuid.
 */
public record usersByRoleDto(String id,
                             String authUserId,
                             String firstName,
                             String lastName,
                             String email,
                             String phone,
                             LocalDate dateOfBirth,
                             String gender,
                             String address,
                             String userName,
                             String role) {}
