package com.spa.home_rental_application.lease_service.config;

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
 * Outbound Feign HMAC signing — ported pattern from auth-service.
 * Without this bean lease-service's Feign calls (property-service,
 * compliance-service, etc.) get rejected as
 * {@code 403 GATEWAY_REQUIRED} by downstream {@code GatewayAuthFilter}.
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
