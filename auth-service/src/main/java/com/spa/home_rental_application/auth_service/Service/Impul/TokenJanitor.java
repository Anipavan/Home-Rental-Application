package com.spa.home_rental_application.auth_service.Service.Impul;

import com.spa.home_rental_application.auth_service.Repository.PasswordResetTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sweeps expired refresh tokens and password-reset tokens. Runs hourly so
 * the auth tables stay tidy even under heavy churn.
 */
@Component
@Slf4j
public class TokenJanitor {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public TokenJanitor(RefreshTokenRepository refreshTokenRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *") // top of every hour
    public void purgeExpired() {
        Instant now = Instant.now();
        int refresh = refreshTokenRepository.deleteAllExpired(now);
        int reset   = passwordResetTokenRepository.deleteAllExpired(now);
        if (refresh > 0 || reset > 0) {
            log.info("Token janitor: purged {} expired refresh + {} expired password-reset tokens",
                    refresh, reset);
        }
    }
}
