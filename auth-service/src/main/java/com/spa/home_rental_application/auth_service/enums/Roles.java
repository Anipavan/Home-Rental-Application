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

    /**
     * Society-only resident. Signed up via the "I'm a maintainee" card
     * on /welcome and had their RESIDENT membership claim approved by
     * the building owner (or auto-approved for owner-occupier
     * scenarios). Occupies a flat for maintenance-billing purposes
     * without a rental relationship with the building owner.
     *
     * <p>Kept distinct from TENANT so the frontend can render a
     * slim, society-focused sidebar (Payments / Society / Profile)
     * instead of the full rental-tenant nav that includes Browse,
     * Lease, Maintenance-request, etc. — surfaces a pure maintainee
     * has no use for.
     *
     * <p>Permission set matches TENANT (read + update) — a maintainee
     * still needs to update their profile, pay dues, etc. Application-
     * level guards (SocietyServiceImpl, MembershipClaimServiceImpl)
     * gate what they can actually see/do; the enum permissions are
     * deliberately broad so the JWT doesn't have to be re-issued the
     * moment someone becomes both a tenant AND a maintainee.
     */
    MAINTAINEE(Set.of(permissions.HRA_READ, permissions.HRA_UPDATE)),

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
