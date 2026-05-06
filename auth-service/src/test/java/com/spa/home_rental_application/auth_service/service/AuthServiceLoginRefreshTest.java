package com.spa.home_rental_application.auth_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLoginEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLogoutEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Entity.PasswordResetToken;
import com.spa.home_rental_application.auth_service.Entity.RefreshToken;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Exception.InvalidTokenException;
import com.spa.home_rental_application.auth_service.Repository.PasswordResetTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.RefreshTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.Impul.AuthServiceImpl;
import com.spa.home_rental_application.auth_service.Service.external.UserServiceFeign;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import com.spa.home_rental_application.auth_service.enums.Roles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginRefreshTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JWTUtil jwtUtil;
    @Mock UserServiceFeign userServiceFeign;
    @Mock AuthServiceEvents events;

    AuthServiceImpl service() {
        JwtProperties props = new JwtProperties();
        props.setSecret("U3VwZXJTZWNyZXRLZXlGb3JKV1RUb2tlbkdlbmVyYXRpb24xMjM0NTY3ODkwIQ==");
        props.setAccessTokenValiditySeconds(3600);
        props.setRefreshTokenValiditySeconds(2_592_000L);
        return new AuthServiceImpl(userRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, authenticationManager, jwtUtil, props,
                userServiceFeign, events, 15L);
    }

    @Test
    void login_success_returnsBearer_andEmitsLoginEvent() {
        UserDetails u = UserDetails.builder().id(7L).userName("alice")
                .userPassword("HASH").userRole(Roles.TENANT)
                .enabled(true).accountNonLocked(true).build();
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", "Strong123"));
        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(u));
        when(jwtUtil.generateToken(any())).thenReturn("ACCESS-JWT");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse r = service().login(new LoginRequest("alice", "Strong123"), "127.0.0.1", "junit");

        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.accessToken()).isEqualTo("ACCESS-JWT");
        assertThat(r.refreshToken()).isNotBlank();
        assertThat(r.role()).isEqualTo("TENANT");

        ArgumentCaptor<UserLoginEvent> evt = ArgumentCaptor.forClass(UserLoginEvent.class);
        verify(events).sendUserLogin(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("user.login");
    }

    @Test
    void login_badCredentials_propagates() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));
        assertThatThrownBy(() -> service().login(new LoginRequest("a", "wrong"), null, null))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(events, refreshTokenRepository);
    }

    @Test
    void refresh_revokesOld_andIssuesNew() {
        RefreshToken old = RefreshToken.builder()
                .id("RT-1").token("old-tok").userId(7L)
                .expiresAt(Instant.now().plusSeconds(60)).revoked(false).build();
        when(refreshTokenRepository.findByToken("old-tok")).thenReturn(Optional.of(old));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        UserDetails u = UserDetails.builder().id(7L).userName("alice").userRole(Roles.TENANT).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(jwtUtil.generateToken(any())).thenReturn("NEW-ACCESS");

        AuthResponse r = service().refresh(new RefreshTokenRequest("old-tok"));

        assertThat(r.accessToken()).isEqualTo("NEW-ACCESS");
        assertThat(r.refreshToken()).isNotEqualTo("old-tok");
        assertThat(old.getRevoked()).isTrue();
    }

    @Test
    void refresh_unknownToken_throwsInvalidToken() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().refresh(new RefreshTokenRequest("missing")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_expiredToken_throwsInvalidToken() {
        RefreshToken expired = RefreshToken.builder().token("exp")
                .userId(7L).expiresAt(Instant.now().minusSeconds(1)).revoked(false).build();
        when(refreshTokenRepository.findByToken("exp")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service().refresh(new RefreshTokenRequest("exp")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refresh_revokedToken_throwsInvalidToken() {
        RefreshToken revoked = RefreshToken.builder().token("rvk")
                .userId(7L).expiresAt(Instant.now().plusSeconds(60)).revoked(true).build();
        when(refreshTokenRepository.findByToken("rvk")).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service().refresh(new RefreshTokenRequest("rvk")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void logout_revokesToken_andEmitsLogoutEvent() {
        RefreshToken t = RefreshToken.builder().token("tok").userId(7L)
                .expiresAt(Instant.now().plusSeconds(60)).revoked(false).build();
        when(refreshTokenRepository.findByToken("tok")).thenReturn(Optional.of(t));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                UserDetails.builder().id(7L).userName("alice").build()));

        service().logout(new LogoutRequest("tok"));

        assertThat(t.getRevoked()).isTrue();
        verify(events).sendUserLogout(any(UserLogoutEvent.class));
    }

    @Test
    void startPasswordReset_unknownEmail_silentlySucceeds() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());
        service().startPasswordReset(new ForgotPasswordRequest("ghost@x.com"));
        // No event because the email isn't registered (defends against enumeration)
        verifyNoInteractions(events);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void startPasswordReset_knownEmail_emitsEventWithToken() {
        UserDetails u = UserDetails.builder().id(7L).userName("alice").email("alice@x.com").build();
        when(userRepository.findByEmailIgnoreCase("alice@x.com")).thenReturn(Optional.of(u));
        when(passwordResetTokenRepository.invalidateAllForUser(7L)).thenReturn(0);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service().startPasswordReset(new ForgotPasswordRequest("alice@x.com"));

        ArgumentCaptor<PasswordResetRequestedEvent> evt = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(events).sendPasswordResetRequested(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("user.password.reset.requested");
        assertThat(evt.getValue().getResetToken()).isNotBlank();
    }

    @Test
    void completePasswordReset_unknownToken_throws() {
        when(passwordResetTokenRepository.findByToken("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().completePasswordReset(new ResetPasswordRequest("missing", "Strong123")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void completePasswordReset_validToken_updatesPassword_revokesAllRefresh() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("rst").userId(7L).expiresAt(Instant.now().plusSeconds(60))
                .used(false).build();
        when(passwordResetTokenRepository.findByToken("rst")).thenReturn(Optional.of(token));
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                UserDetails.builder().id(7L).userName("alice").userPassword("OLD").build()));
        when(passwordEncoder.encode("Strong123New")).thenReturn("HASH");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.revokeAllForUser(7L)).thenReturn(2);

        service().completePasswordReset(new ResetPasswordRequest("rst", "Strong123New"));

        assertThat(token.getUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(7L);
    }
}
