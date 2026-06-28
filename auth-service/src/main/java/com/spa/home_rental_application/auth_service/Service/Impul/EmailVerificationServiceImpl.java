package com.spa.home_rental_application.auth_service.Service.Impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.EmailVerificationRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.auth_service.Entity.EmailVerificationToken;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Exception.AuthRecordNotFoundException;
import com.spa.home_rental_application.auth_service.Exception.InvalidTokenException;
import com.spa.home_rental_application.auth_service.Repository.EmailVerificationTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.EmailVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * SecureRandom-backed token mint, Kafka-driven email dispatch, and the
 * verify / resend bookkeeping for the {@code email_verified} gate.
 *
 * <p>Token shape: 32 random bytes → URL-safe Base64 (no padding) = 43
 * chars, well under the column's VARCHAR2(64) cap. Stored as
 * plain-text rather than hashed because email-verification tokens are
 * single-use + short-lived + low-consequence (a stolen token only lets
 * the attacker mark someone else's account as verified, which isn't a
 * useful escalation path).
 */
@Service
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final SecureRandom RAND = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final AuthServiceEvents authEvents;
    private final Duration ttl;
    private final int maxResendsPerHour;

    public EmailVerificationServiceImpl(
            EmailVerificationTokenRepository tokenRepo,
            UserRepository userRepo,
            AuthServiceEvents authEvents,
            @Value("${app.email-verification.token-ttl-hours:24}") long ttlHours,
            @Value("${app.email-verification.max-resends-per-hour:3}") int maxResendsPerHour) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.authEvents = authEvents;
        this.ttl = Duration.ofHours(ttlHours);
        this.maxResendsPerHour = maxResendsPerHour;
    }

    @Override
    @Transactional
    public void mintAndDispatch(UserDetails user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            log.debug("mintAndDispatch no-op for authUserId={} — already verified", user.getId());
            return;
        }
        // Invalidate any prior live tokens so an old link in the user's
        // inbox can't be replayed after this fresh one goes out.
        tokenRepo.invalidateAllForUser(user.getId(), Instant.now());

        String raw = generateToken();
        Instant expiresAt = Instant.now().plus(ttl);
        EmailVerificationToken row = EmailVerificationToken.builder()
                .token(raw)
                .userId(user.getId())
                .expiresAt(expiresAt)
                .build();
        tokenRepo.save(row);

        authEvents.sendEmailVerificationRequested(EmailVerificationRequestedEvent.builder()
                .eventType("user.email.verification.requested")
                .authUserId(user.getId().toString())
                .userName(user.getUsername())
                .email(user.getEmail())
                .token(raw)
                .expiresAt(expiresAt)
                .timestamp(Instant.now())
                .build());
        log.info("Email verification token minted for authUserId={} ttl={}h", user.getId(), ttl.toHours());
    }

    @Override
    @Transactional
    public UserDetails verify(String rawToken) {
        EmailVerificationToken row = tokenRepo.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException(
                        "Verification link is invalid. Request a fresh one to continue."));

        if (row.isConsumed()) {
            // Treat as already-verified — a second click on the same link
            // is benign. Refuse so the UI can render "already verified".
            throw new InvalidTokenException(
                    "This verification link has already been used.");
        }
        if (row.isExpired()) {
            throw new InvalidTokenException(
                    "Verification link has expired. Request a fresh one to continue.");
        }

        UserDetails user = userRepo.findById(row.getUserId())
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found for token: " + rawToken));

        row.setConsumedAt(Instant.now());
        tokenRepo.save(row);

        user.setEmailVerified(true);
        user.setRecodeUpdatedDate(Instant.now());
        userRepo.save(user);

        log.info("Email verified for authUserId={} email={}", user.getId(), user.getEmail());
        return user;
    }

    @Override
    @Transactional
    public void resend(String email) {
        UserDetails user = userRepo.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            // Don't leak which emails are registered. The /resend endpoint
            // always returns 200 even when the email is unknown.
            log.debug("resend ignored for unknown email={}", email);
            return;
        }
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            log.debug("resend ignored for authUserId={} — already verified", user.getId());
            return;
        }

        Instant windowStart = Instant.now().minus(Duration.ofHours(1));
        long recent = tokenRepo.countByUserIdAndCreatedAtAfter(user.getId(), windowStart);
        if (recent >= maxResendsPerHour) {
            throw new IllegalStateException(
                    "Too many verification emails requested. Try again in a few minutes.");
        }

        mintAndDispatch(user);
    }

    private String generateToken() {
        byte[] buf = new byte[32];
        RAND.nextBytes(buf);
        return B64.encodeToString(buf);
    }
}
