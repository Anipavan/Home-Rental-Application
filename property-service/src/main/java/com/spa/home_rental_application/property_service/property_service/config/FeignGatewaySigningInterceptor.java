package com.spa.home_rental_application.property_service.property_service.config;

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
 * outbound Feign call made from property-service.
 *
 * <p>Ported from {@code auth-service}'s identical config. Without this
 * bean, property-service's {@link com.spa.home_rental_application.property_service.property_service.client.UserClient}
 * Feign calls to user-service get rejected with
 * {@code 403 GATEWAY_REQUIRED} — user-service's {@code GatewayAuthFilter}
 * sees an unsigned request and refuses it. That's what was making the
 * lease PDF's owner/tenant name lookup silently fail to "(name on file)"
 * fallback even though both services were healthy.
 *
 * <p>The signing path used in the HMAC is the Feign template URL after
 * Eureka resolution (e.g. {@code /users/auth/<authUserId>}) — matches
 * what the downstream {@code GatewayAuthFilter} verifies on its end.
 *
 * <p>We also forward the caller's {@code X-Auth-User-Name} /
 * {@code X-Auth-User-Id} / {@code X-Auth-Roles} headers when the call
 * runs inside a request-scoped context, so the downstream service's
 * {@code @PreAuthorize} can build a real Authentication. For Feign
 * calls that happen outside a request (Kafka listeners, schedulers,
 * etc.) those headers are simply omitted — the HMAC signature alone is
 * still enough for the downstream filter to grant service-to-service
 * trust.
 *
 * <p><b>Why this file exists in property-service instead of
 * auth-commons.</b> auth-commons doesn't have a Feign dependency on its
 * compile classpath, and adding one would force every consumer (including
 * the gateway, which is reactive Spring Cloud Gateway and intentionally
 * avoids servlet-Feign) to pull it in. Keeping a copy here is the
 * pragmatic choice — the interceptor logic is ~50 lines, shouldn't drift,
 * and the alternative is a much heavier shared-config refactor.
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

            // Propagate the caller's identity headers when we're inside
            // a request-scoped context. Outside one (Kafka listener,
            // CommandLineRunner, scheduled job) the forwarding is
            // skipped — the HMAC signature alone is sufficient for
            // service-to-service trust.
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
     * For Feign + Spring Cloud LoadBalancer the {@code template.url()}
     * is typically a path like {@code /users/auth/<id>} (the loadbalancer
     * will later substitute the host). If a full URL is present we
     * extract its path component. Query strings are intentionally
     * stripped — they're not part of the HMAC payload on either side.
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
