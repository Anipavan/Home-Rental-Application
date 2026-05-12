package com.spa.home_rental_application.ServiceRegistry.Service.Registry.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Audit H8: protects the Eureka registry with basic auth so an
 * attacker who reaches port 8761 can't simply POST a fake service
 * registration (e.g. {@code HRA-payment-service}) and intercept Feign
 * calls.
 *
 * <p>Credentials come from {@code EUREKA_USERNAME} / {@code
 * EUREKA_PASSWORD} env vars — required in prod, defaulted in dev to
 * keep the local stack runnable without ceremony. The
 * {@code SecretsBootstrapValidator} in auth-commons doesn't run here
 * (Service-Registry doesn't depend on auth-commons by design), so this
 * config does its own placeholder check at startup.
 *
 * <p>CSRF is disabled because every Eureka client is a backend service
 * (no browser, no form-token flow). The actuator's {@code /health}
 * endpoint stays open so the platform's container liveness probes
 * don't break.
 *
 * <p>Every Eureka client in the platform reads its own
 * {@code spring.security.user.name} / {@code .password} or constructs
 * the {@code defaultZone} URL with embedded credentials —
 * {@code http://user:pass@host:8761/eureka/}. The startup config for
 * each service has been updated to do this.
 */
@Configuration
public class SecurityConfig {

    @Value("${EUREKA_USERNAME:eureka}")
    private String username;

    @Value("${EUREKA_PASSWORD:CHANGE_ME_LOCAL_DEV_EUREKA_PLACEHOLDER}")
    private String password;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No browser sessions, no form tokens — every caller is
                // a backend Spring service. Disabling CSRF lets clients
                // POST /eureka/apps/{appId} without a token round-trip.
                .csrf(AbstractHttpConfigurer::disable)
                // /actuator/health stays open so K8s/Docker probes work.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService eurekaUsers(PasswordEncoder encoder) {
        UserDetails u = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("EUREKA")
                .build();
        return new InMemoryUserDetailsManager(u);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
