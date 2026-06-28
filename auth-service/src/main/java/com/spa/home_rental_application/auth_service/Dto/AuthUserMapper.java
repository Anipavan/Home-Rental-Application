package com.spa.home_rental_application.auth_service.Dto;

import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.enums.Roles;

import java.util.List;

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
                rolesList(u),
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
                rolesList(u),
                u.getRecordCreatedDate()
        );
    }

    /**
     * Project the multi-role union onto a stable, sorted list of role
     * names. Sorted alphabetically so wire output is deterministic
     * (helpful for tests, snapshot diffs, and humans scanning logs).
     */
    private static List<String> rolesList(UserDetails u) {
        return u.getAllRoles().stream()
                .map(Roles::name)
                .sorted()
                .toList();
    }
}
