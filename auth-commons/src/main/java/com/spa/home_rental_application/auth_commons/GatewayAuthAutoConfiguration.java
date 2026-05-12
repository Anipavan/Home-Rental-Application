package com.spa.home_rental_application.auth_commons;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Auto-config that wires {@link GatewayAuthFilter} into every downstream
 * service that depends on this jar.
 * <p>
 * Activated by default; can be disabled with {@code app.internal-auth.enabled=false}.
 * Services that already define their own {@link SecurityFilterChain} (e.g.
 * the Auth Service) will keep theirs — they should manually inject and
 * register the {@link GatewayAuthFilter} bean in their own chain.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "app.internal-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GatewaySignatureProperties.class)
public class GatewayAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GatewaySignatureVerifier gatewaySignatureVerifier(GatewaySignatureProperties props) {
        return new GatewaySignatureVerifier(props.getSecret(), props.getAllowedClockSkewSeconds());
    }

    /**
     * Refuse to serve traffic if the canned dev placeholder secrets are
     * still in play under a non-dev profile. Bean is unconditional —
     * every service that depends on auth-commons gets the check, which
     * is exactly the goal.
     */
    @Bean
    @ConditionalOnMissingBean
    public SecretsBootstrapValidator secretsBootstrapValidator() {
        return new SecretsBootstrapValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayAuthFilter gatewayAuthFilter(GatewaySignatureProperties props,
                                               GatewaySignatureVerifier verifier) {
        return new GatewayAuthFilter(props, verifier);
    }

    /**
     * Default filter chain for services that don't define their own.
     * All requests must pass {@link GatewayAuthFilter}; CSRF disabled (we are
     * stateless, behind the gateway); CORS handled at the gateway.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain gatewayAuthSecurityFilterChain(HttpSecurity http,
                                                              GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
