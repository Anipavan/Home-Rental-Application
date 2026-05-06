package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Computes the HMAC signature over {@code timestamp:method:path} and adds
 * it (with the timestamp) to every proxied request as
 * {@code X-Internal-Auth-Sig} and {@code X-Internal-Auth-Ts}. Downstream
 * services use {@code GatewayAuthFilter} from {@code auth-commons} to
 * verify. This is what enforces "must come through the gateway".
 *
 * <p><b>Order:</b> runs AFTER all per-route filters (most importantly
 * {@code StripPrefix=1}) so the path we sign matches the path the
 * downstream service will actually receive in {@code request.getRequestURI()}.
 * Specifically we run between {@link RouteToRequestUrlFilter} (which sets
 * {@link ServerWebExchangeUtils#GATEWAY_REQUEST_URL_ATTR}) and
 * {@link NettyRoutingFilter} (which actually issues the downstream HTTP
 * request).
 */
@Component
public class GatewaySigningFilter implements GlobalFilter, Ordered {

    /**
     * Run after {@code RouteToRequestUrlFilter} (order 10000) but well
     * before {@code NettyRoutingFilter} ({@link Ordered#LOWEST_PRECEDENCE}).
     * At this point the {@code GATEWAY_REQUEST_URL_ATTR} reflects the
     * post-StripPrefix path that the downstream service will see.
     */
    public static final int ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 100;

    private final GatewaySigner signer;

    public GatewaySigningFilter(GatewaySigner signer) {
        this.signer = signer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI requestUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String pathToSign = requestUri != null ? requestUri.getRawPath()
                                               : exchange.getRequest().getURI().getRawPath();
        String method = exchange.getRequest().getMethod().name();

        GatewaySigner.Signature s = signer.sign(method, pathToSign);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.set("X-Internal-Auth-Sig", s.signature());
                    h.set("X-Internal-Auth-Ts",  Long.toString(s.timestamp()));
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
