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
