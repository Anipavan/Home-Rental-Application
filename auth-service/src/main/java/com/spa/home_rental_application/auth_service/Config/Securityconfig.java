package com.spa.home_rental_application.auth_service.Config;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Auth Service security chain.
 * <ul>
 *   <li>Stateless (JWT-based, no HTTP session)</li>
 *   <li>CSRF disabled (no cookies, no browser forms)</li>
 *   <li>Public endpoints: login, register, refresh, forgot/reset-password,
 *       Swagger, actuator health/info</li>
 *   <li>Everything else requires a valid JWT, with role checks via
 *       {@code @PreAuthorize} on the controller methods</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class Securityconfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/register",
            "/auth/register/pending",
            "/auth/login",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            // Audit H3 (gateway side): the gateway calls
            // /auth/internal/tokens-revoked-before/{userId} on every
            // request to enforce immediate logout. The call is gated
            // by the GatewayAuthFilter (HMAC verification) so it
            // doesn't need an additional JWT — that would actually be
            // circular (JWT-validate path calling JWT-validate). We
            // permit it at the Spring Security layer; HMAC is the
            // real control.
            "/auth/internal/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Audit H6: enable HSTS + standard security headers so any
                // direct HTTP (not gateway-fronted) traffic hardens
                // automatically. Browsers will refuse to downgrade to
                // plain HTTP once they've seen the HSTS header. The
                // gateway terminates TLS in prod; setting these here is
                // defence-in-depth in case the load balancer is ever
                // bypassed.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000))   // 1 year
                        .contentTypeOptions(c -> {})            // X-Content-Type-Options: nosniff
                        .frameOptions(f -> f.deny())            // X-Frame-Options: DENY
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/register/pending",
                                "/auth/login", "/auth/refresh", "/auth/forgot-password",
                                "/auth/reset-password",
                                // V16 — public because the recipient
                                // clicks the link before they're logged
                                // in. Token in the body is the credential.
                                "/auth/verify-email", "/auth/resend-verification").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                // GatewayAuthFilter runs FIRST. It blocks any direct hit to this service
                // (no/invalid X-Internal-Auth-Sig). The local JwtAuthenticationFilter
                // then validates the access token IF present.
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, com.spa.home_rental_application.auth_commons.GatewayAuthFilter.class);
        return http.build();
    }

    /**
     * Audit M4: BCrypt cost factor pinned to 12. Default is 10 — fine
     * for legacy use but the modern recommendation (and OWASP 2023+)
     * is 12 on commodity hardware. The bump roughly quadruples
     * hash-time (~100ms → ~400ms on a typical container CPU) which
     * is imperceptible during real logins but quadruples the cost
     * of an offline brute-force run against a leaked DB.
     *
     * <p>Existing hashes are forward-compatible: BCrypt embeds the
     * cost factor in the stored hash, so old-cost passwords still
     * validate. They'll be re-hashed at the next successful login
     * via a separate upgrade hook (tracked).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
