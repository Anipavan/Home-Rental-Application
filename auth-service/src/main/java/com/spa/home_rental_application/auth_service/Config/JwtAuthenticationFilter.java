package com.spa.home_rental_application.auth_service.Config;

import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on every request,
 * extracts the subject + authorities, and seeds the {@link SecurityContextHolder}
 * so {@code @PreAuthorize} can enforce role checks downstream.
 * <p>
 * Public endpoints (login/register/forgot-password/etc.) skip this because the
 * security chain in {@link Securityconfig} permits them anonymously.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JWTUtil jwtUtil;

    public JwtAuthenticationFilter(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(PREFIX.length()).trim();
        try {
            String subject = jwtUtil.extractSubject(token);
            List<SimpleGrantedAuthority> authorities = jwtUtil.extractAuthorities(token).stream()
                    .map(SimpleGrantedAuthority::new).toList();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("JWT authenticated subject={} authorities={}", subject, authorities);
        } catch (JwtException ex) {
            log.warn("Rejected invalid JWT on {}: {}", request.getRequestURI(), ex.getMessage());
            // Do not throw — let the security chain return 401 if the endpoint requires auth.
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
