package com.spa.home_rental_application.auth_service.Dto.External;

import java.time.LocalDate;

/**
 * Payload sent to User Service when a new account is registered.
 * Crucially this DTO does NOT include the password — only the auth-side
 * id and the profile fields User Service is going to persist.
 */
public record UserProfileCreateRequest(
        String authUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String profilePictureUrl,
        String idProofUrl
) {}
