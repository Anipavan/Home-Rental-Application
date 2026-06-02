package com.spa.home_rental_application.auth_service.enums;

import java.util.Set;

/**
 * System roles. Each role is bound to a fixed permission set used by
 * Spring Security {@code @PreAuthorize} annotations.
 * <p>
 * {@link #TENENT} is retained as a deprecated alias for {@link #TENANT}
 * because legacy DB rows may still carry the misspelling. New code must
 * use {@code TENANT}.
 */
public enum Roles {
    ADMIN(Set.of(permissions.HRA_WRITE, permissions.HRA_READ,
            permissions.HRA_DELETE, permissions.HRA_UPDATE)),

    OWNER(Set.of(permissions.HRA_WRITE, permissions.HRA_READ,
            permissions.HRA_DELETE, permissions.HRA_UPDATE)),

    /**
     * Society / common-area maintainer for one or more buildings.
     * Same broad permission set as OWNER (their main job is to write
     * expense rows + collection markings), but in practice they're
     * scoped to the buildings their {@code authUserId} appears as
     * {@code maintainer_user_id} on. Application-level checks
     * (SocietyServiceImpl) enforce per-building scope; the role
     * permissions are deliberately broad so the same JWT doesn't
     * need to be re-issued every time an owner re-assigns the
     * maintainer for a different building.
     */
    MAINTAINER(Set.of(permissions.HRA_WRITE, permissions.HRA_READ,
            permissions.HRA_DELETE, permissions.HRA_UPDATE)),

    TENANT(Set.of(permissions.HRA_READ, permissions.HRA_UPDATE)),

    /** @deprecated misspelled — use {@link #TENANT}. */
    @Deprecated
    TENENT(Set.of(permissions.HRA_READ, permissions.HRA_UPDATE));

    private final Set<permissions> rolePermissions;

    Roles(Set<permissions> rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    public Set<permissions> getPermissions() {
        return rolePermissions;
    }
}
