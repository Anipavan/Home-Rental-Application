package com.spa.home_rental_application.user_service.user_service.config;

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
 * outbound Feign call so the downstream {@code GatewayAuthFilter} accepts
 * the request.
 *
 * <p>It also forwards the originating user-context headers
 * ({@code X-Auth-User-Name}, {@code X-Auth-User-Id}, {@code X-Auth-Roles})
 * pulled off the current servlet request. Without this, downstream
 * {@code @PreAuthorize}-protected endpoints would 403 because the gateway
 * filter on the receiving end has no authenticated principal to construct.
 *
 * <p>Used by user-service when it calls auth-service ({@code AuthServiceFeig})
 * and property-service ({@code PropertyServiceFeig}).
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

            // Propagate the caller's identity so the receiving service's
            // @PreAuthorize checks see the right roles. Best-effort: when
            // we're outside an HTTP request scope (scheduled job, Kafka
            // listener, etc.) the headers are simply omitted and the call
            // hits an endpoint that should be open to internal services.
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
