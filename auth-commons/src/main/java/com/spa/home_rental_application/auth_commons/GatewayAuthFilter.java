package com.spa.home_rental_application.auth_commons;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Servlet filter that enforces "this request must come through the API
 * Gateway" on every downstream service.
 *
 * <p>Headers expected from the gateway:
 * <ul>
 *   <li>{@code X-Internal-Auth-Sig} — HMAC-SHA256 hex of {@code ts:method:path}</li>
 *   <li>{@code X-Internal-Auth-Ts} — epoch seconds</li>
 *   <li>{@code X-Auth-User-Name} — authenticated subject (optional for public paths at Gateway)</li>
 *   <li>{@code X-Auth-User-Id}    — id of the auth-side user record (optional)</li>
 *   <li>{@code X-Auth-Roles}      — comma-separated role/authority list (optional)</li>
 * </ul>
 *
 * Behaviour:
 * <ol>
 *   <li>If the URI matches {@link GatewaySignatureProperties#getPublicPaths()},
 *       the filter passes through (e.g. for {@code /actuator/health} probes).</li>
 *   <li>Otherwise it verifies the signature. On any failure it short-circuits
 *       the chain with {@code 403 GATEWAY_REQUIRED}.</li>
 *   <li>On success it builds a Spring {@link UsernamePasswordAuthenticationToken}
 *       from the {@code X-Auth-*} headers so {@code @PreAuthorize} works in
 *       controller code.</li>
 * </ol>
 */
@Slf4j
public class GatewayAuthFilter extends OncePerRequestFilter {

    public static final String HDR_SIG  = "X-Internal-Auth-Sig";
    public static final String HDR_TS   = "X-Internal-Auth-Ts";
    public static final String HDR_USER = "X-Auth-User-Name";
    public static final String HDR_UID  = "X-Auth-User-Id";
    public static final String HDR_ROLES = "X-Auth-Roles";

    private static final PathMatcher MATCHER = new AntPathMatcher();

    private final GatewaySignatureProperties props;
    private final GatewaySignatureVerifier verifier;

    public GatewayAuthFilter(GatewaySignatureProperties props,
                             GatewaySignatureVerifier verifier) {
        this.props = props;
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!props.isEnabled()) {
            // Disabled mode: skip verification entirely (used by tests/local dev)
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sig = request.getHeader(HDR_SIG);
        String ts  = request.getHeader(HDR_TS);
        String method = request.getMethod().toUpperCase();

        GatewaySignatureVerifier.Outcome outcome = verifier.verify(ts, sig, method, path);
        if (!outcome.isValid()) {
            log.warn("Direct-access blocked: outcome={} ip={} path={} method={}",
                    outcome, request.getRemoteAddr(), path, method);
            writeError(response, HttpStatus.FORBIDDEN,
                    "Requests must originate from the API Gateway. Reason: " + outcome.name(),
                    "GATEWAY_REQUIRED",
                    path);
            return;
        }

        // Signature accepted — populate Authentication from the gateway-supplied headers.
        String userName = request.getHeader(HDR_USER);
        String roles = request.getHeader(HDR_ROLES);

        if (userName != null && !userName.isBlank()) {
            List<SimpleGrantedAuthority> authorities = (roles == null || roles.isBlank())
                    ? List.of()
                    : Arrays.stream(roles.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(SimpleGrantedAuthority::new)
                            .toList();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userName, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPublicPath(String path) {
        if (props.getPublicPaths() == null) return false;
        for (String pattern : props.getPublicPaths()) {
            if (MATCHER.match(pattern, path)) return true;
        }
        return false;
    }

    private void writeError(HttpServletResponse resp, HttpStatus status,
                            String message, String code, String path) throws IOException {
        // Hand-rolled JSON to keep auth-commons free of a Jackson runtime dep.
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
          .append("\"timestamp\":\"").append(LocalDateTime.now()).append("\",")
          .append("\"status\":").append(status.value()).append(',')
          .append("\"error\":\"").append(escape(status.getReasonPhrase())).append("\",")
          .append("\"message\":\"").append(escape(message)).append("\",")
          .append("\"errorCode\":\"").append(escape(code)).append("\",")
          .append("\"path\":\"").append(escape(path)).append("\"")
          .append('}');
        resp.setStatus(status.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.getWriter().write(sb.toString());
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"'  -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
