package com.spa.home_rental_application.property_service.property_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for auth-service internal endpoints. Currently only the
 * "promote tenant to maintainer" flow needs auth-service to mutate a
 * user account from property-service; we add new methods as more
 * inter-service flows arrive.
 *
 * <p>The {@link FeignGatewaySigningInterceptor} sibling config stamps
 * the {@code X-Internal-Auth-Sig} HMAC on every call so the request
 * passes auth-service's GatewayAuthFilter — same plumbing used by the
 * existing {@link UserClient} and {@link NotificationClient}.
 *
 * <p>Failures bubble up as Feign exceptions and are translated into a
 * 502/503 by the controller's global handler. We intentionally do NOT
 * have a fallback bean: the promote flow is mutating + idempotency-
 * sensitive (we want the caller to see the exact failure rather than
 * a silent "looks-OK" empty response).
 */
@FeignClient(name = "HRA-auth-service")
public interface AuthClient {

    /**
     * Mirrors auth-service {@code POST /auth/internal/users/{authUserId}/promote-to-maintainer}.
     * Returns the post-mutation {@link AuthUserSummary}.
     */
    @PostMapping(value = "/auth/internal/users/{authUserId}/promote-to-maintainer",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    AuthUserSummary promoteToMaintainer(@PathVariable("authUserId") Long authUserId,
                                        @RequestBody PromoteBody body);

    /**
     * Mirrors auth-service {@code POST /auth/internal/users/{authUserId}/grant-maintainer-role}.
     *
     * <p>Used by the self-service membership-claim approval flow. Unlike
     * {@link #promoteToMaintainer}, this does NOT set a maintainer
     * password — the user already chose their own password at signup
     * and we don't want to invalidate it. The endpoint simply bumps
     * {@code user_role} to MAINTAINER so role-gated routes pass.
     *
     * <p>The dual-credential mode ({@code maintainer_password} column)
     * exists for the older owner-promote-existing-tenant flow where the
     * caller didn't know the tenant's password. Self-registered
     * maintainers don't need it.
     */
    @PostMapping(value = "/auth/internal/users/{authUserId}/grant-maintainer-role")
    AuthUserSummary grantMaintainerRole(@PathVariable("authUserId") Long authUserId);

    /** Inbound body for the promote call. Mirrors auth-service's
     *  {@code PromoteToMaintainerRequest}. */
    record PromoteBody(String newPassword) {}

    /** Slimmed-down projection of auth-service's {@code AuthUserResponse}.
     *  Only fields property-service needs to render the post-promotion
     *  confirmation. Extra fields the server returns are ignored on
     *  deserialization (Jackson defaults). */
    record AuthUserSummary(
            String id,
            String userName,
            String userRole,
            String email
    ) {}
}
