package com.spa.home_rental_application.user_service.user_service.config;

import com.spa.home_rental_application.auth_commons.GatewaySigner;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Feign request interceptor that adds the gateway HMAC headers
 * ({@code X-Internal-Auth-Sig} and {@code X-Internal-Auth-Ts}) to every
 * outbound Feign call so the downstream {@code GatewayAuthFilter} accepts
 * the request.
 *
 * <p>Used by user-service when it calls auth-service ({@code AuthServiceFeig})
 * and property-service ({@code PropertyServiceFeig}).
 */
@Configuration
@Slf4j
public class FeignGatewaySigningInterceptor {

    @Bean
    public RequestInterceptor gatewaySigningRequestInterceptor(GatewaySigner signer) {
        return template -> {
            String method = template.method() == null ? "GET" : template.method().toUpperCase();
            String path = extractPath(template);

            GatewaySigner.Signature s = signer.sign(method, path);
            template.header("X-Internal-Auth-Sig", s.signature());
            template.header("X-Internal-Auth-Ts",  Long.toString(s.timestamp()));

            log.debug("Signed outbound Feign call {} {} (ts={})", method, path, s.timestamp());
        };
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
