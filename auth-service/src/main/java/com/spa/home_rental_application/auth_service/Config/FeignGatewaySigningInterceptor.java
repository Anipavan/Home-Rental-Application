package com.spa.home_rental_application.auth_service.Config;

import com.spa.home_rental_application.auth_commons.GatewaySigner;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Feign request interceptor that adds the gateway HMAC headers
 * ({@code X-Internal-Auth-Sig} and {@code X-Internal-Auth-Ts}) to every
 * outbound Feign call.
 *
 * <p>This is what allows direct service-to-service calls (e.g. auth-service
 * → user-service via {@code UserServiceFeign}) to satisfy the downstream
 * {@code GatewayAuthFilter}. Without it, the downstream service would
 * reject the call with {@code 403 GATEWAY_REQUIRED} because no API Gateway
 * was in the chain to sign the request.
 *
 * <p>The path used in the HMAC is the request URI path that the downstream
 * will see (i.e. the path component of the Feign target URL after Eureka
 * resolution: e.g. {@code /users/user}). We deliberately do NOT include
 * the query string — matches what {@code GatewayAuthFilter} verifies.
 *
 * <p>Note: we also propagate the caller's {@code X-Auth-User-Name} and
 * {@code X-Auth-Roles} headers from the inbound request when present, so
 * the downstream service sees the original user context. (Currently only
 * applies when the auth-side {@code SecurityContextHolder} carries the
 * Authentication; the Feign call itself is unauthenticated by design.)
 */
@Configuration
@Slf4j
public class FeignGatewaySigningInterceptor {

    private static final String HDR_USER  = "X-Auth-User-Name";
    private static final String HDR_UID   = "X-Auth-User-Id";
    private static final String HDR_ROLES = "X-Auth-Roles";

    @Bean
    public RequestInterceptor gatewaySigningRequestInterceptor(GatewaySigner signer) {
        return template -> {
            String method = template.method() == null ? "GET" : template.method().toUpperCase();
            String path = extractPath(template);

            GatewaySigner.Signature s = signer.sign(method, path);
            template.header("X-Internal-Auth-Sig", s.signature());
            template.header("X-Internal-Auth-Ts",  Long.toString(s.timestamp()));

            // Propagate the caller's identity so the downstream service's
            // @PreAuthorize can construct a real Authentication. Without
            // this the downstream sees an HMAC-signed but anonymous
            // request and returns 403 on any role-protected endpoint.
            HttpServletRequest req = currentRequest();
            if (req != null) {
                copyHeaderIfPresent(req, template, HDR_USER);
                copyHeaderIfPresent(req, template, HDR_UID);
                copyHeaderIfPresent(req, template, HDR_ROLES);
            }

            log.debug("Signed outbound Feign call {} {} (ts={})", method, path, s.timestamp());
        };
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private static void copyHeaderIfPresent(HttpServletRequest req,
                                            RequestTemplate template,
                                            String name) {
        String v = req.getHeader(name);
        if (v != null && !v.isBlank()) {
            template.header(name, v);
        }
    }

    /**
     * Extracts the request URI path the downstream service will see.
     * For Feign + Spring Cloud LoadBalancer the {@code template.url()} is
     * typically a path like {@code /users/user} (the loadbalancer will
     * later substitute the host). If a full URL is present we extract its
     * path component.
     */
    private static String extractPath(RequestTemplate template) {
        String url = template.url();
        if (url == null || url.isEmpty()) return "/";
        if (url.startsWith("/")) {
            int q = url.indexOf('?');
            return q < 0 ? url : url.substring(0, q);
        }
        try {
            URI uri = new URI(url);
            String p = uri.getRawPath();
            return (p == null || p.isEmpty()) ? "/" : p;
        } catch (URISyntaxException ex) {
            return url;
        }
    }
}
