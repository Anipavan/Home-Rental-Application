package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewaySigningFilterTest {

    private static final String SECRET = "Z2F0ZXdheS1zaGFyZWQtc2VjcmV0LWNoYW5nZS1tZS1pbi1wcm9k";

    @Test
    void order_runsAfterRouteToRequestUrl_butBeforeNettyRouter() {
        GatewaySigningFilter f = new GatewaySigningFilter(new GatewaySigner(SECRET, 60));
        // Must run after the route URL has been set
        assertThat(f.getOrder()).isGreaterThan(RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER);
        // Must still run before the actual outbound HTTP call
        assertThat(f.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void filter_addsSigAndTimestampHeaders_signedFromRoutedPath() {
        GatewaySigningFilter f = new GatewaySigningFilter(new GatewaySigner(SECRET, 60));

        // Inbound path /api/auth/login. After StripPrefix=1 the GATEWAY_REQUEST_URL_ATTR
        // would be /auth/login — set it explicitly to emulate that.
        MockServerHttpRequest req = MockServerHttpRequest
                .method(HttpMethod.POST, URI.create("http://gateway/api/auth/login"))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
                URI.create("http://localhost/auth/login"));

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        f.filter(exchange, chain).block();

        // The chain should have been called with a mutated exchange
        org.mockito.ArgumentCaptor<org.springframework.web.server.ServerWebExchange> cap =
                org.mockito.ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        org.mockito.Mockito.verify(chain).filter(cap.capture());
        var headers = cap.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Internal-Auth-Sig")).isNotBlank();
        assertThat(headers.getFirst("X-Internal-Auth-Ts")).isNotBlank();
        // Timestamp should be a parseable epoch second
        long ts = Long.parseLong(headers.getFirst("X-Internal-Auth-Ts"));
        assertThat(ts).isGreaterThan(0L);
    }
}
