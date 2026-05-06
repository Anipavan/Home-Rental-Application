package com.spa.home_rental_application.auth_commons;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GatewayAuthFilterTest {

    private static final String SECRET = "Z2F0ZXdheS1zaGFyZWQtc2VjcmV0LWNoYW5nZS1tZS1pbi1wcm9k";
    private final GatewaySignatureProperties props = new GatewaySignatureProperties();
    private final GatewaySignatureVerifier verifier = new GatewaySignatureVerifier(SECRET, 60);
    private final GatewaySigner signer = new GatewaySigner(SECRET, 60);
    private final GatewayAuthFilter filter = new GatewayAuthFilter(props, verifier);

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        props.setEnabled(true);
        props.setSecret(SECRET);
    }

    @Test
    void publicPath_passesThrough_evenWithoutSignature() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void disabled_skipsVerification() throws Exception {
        props.setEnabled(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void missingSignature_blocksWith403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("GATEWAY_REQUIRED");
    }

    @Test
    void validSignature_seedsAuthenticationFromHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        long ts = Instant.now().getEpochSecond();
        String sig = signer.sign("POST", "/auth/login").signature();
        req.addHeader(GatewayAuthFilter.HDR_SIG, sig);
        req.addHeader(GatewayAuthFilter.HDR_TS, ts);
        req.addHeader(GatewayAuthFilter.HDR_USER, "alice");
        req.addHeader(GatewayAuthFilter.HDR_ROLES, "ROLE_TENANT,HRA_READ");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // Capture the Authentication that was set during the chain
        FilterChain chain = (q, r) -> {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            assertThat(a).isNotNull();
            assertThat(a.getName()).isEqualTo("alice");
            assertThat(a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                    .containsExactlyInAnyOrder("ROLE_TENANT", "HRA_READ");
        };
        filter.doFilter(req, resp, chain);

        // After the chain, the filter must clear context so threads don't leak
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void wrongSignature_blocksWith403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        req.addHeader(GatewayAuthFilter.HDR_SIG, "deadbeef");
        req.addHeader(GatewayAuthFilter.HDR_TS, Instant.now().getEpochSecond());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, resp, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(403);
    }
}
