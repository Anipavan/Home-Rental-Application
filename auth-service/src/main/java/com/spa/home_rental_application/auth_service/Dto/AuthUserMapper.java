package com.spa.home_rental_application.auth_service.Dto;

import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;

/**
 * Entity ↔ DTO mappers. Lives in its own class so controllers/services
 * never reach into entity setters/getters directly when shaping responses.
 */
public final class AuthUserMapper {

    private AuthUserMapper() {}

    public static AuthUserResponse toAuthUserResponse(UserDetails u) {
        if (u == null) return null;
        return new AuthUserResponse(
                u.getId() != null ? u.getId().toString() : null,
                u.getUsername(),
                u.getUserRole() != null ? u.getUserRole().name() : null,
                u.getEmail(),
                u.getRecordCreatedDate(),
                u.getRecodeUpdatedDate()
        );
    }

    public static RegisterResponse toRegisterResponse(UserDetails u) {
        if (u == null) return null;
        return new RegisterResponse(
                u.getId() != null ? u.getId().toString() : null,
                u.getUsername(),
                u.getEmail(),
                u.getUserRole() != null ? u.getUserRole().name() : null,
                u.getRecordCreatedDate()
        );
    }
}
