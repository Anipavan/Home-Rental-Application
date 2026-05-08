package com.spa.home_rental_application.user_service.user_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Subscribes to {@code auth-events} and idempotently creates a User Service
 * profile row when a {@code user.registered} event arrives.
 *
 * <p>Background:
 *
 * <p>Auth Service's registration flow already calls User Service via Feign
 * to create the profile inline ({@code AuthServiceImpl.register} →
 * {@code UserServiceFeign.createUser}). When that synchronous path
 * succeeds, this listener is a no-op (the row already exists, so we exit
 * early on the auth-id check).
 *
 * <p>The listener exists for the cases where the synchronous path
 * <em>didn't</em> persist a profile:
 * <ul>
 *   <li><b>Circuit open / transient outage.</b> The Feign call hits
 *       {@code UserServiceFeignFallbackFactory} which throws 503; the
 *       outer transaction is supposed to roll back the auth-side row,
 *       but if the rollback itself fails or a downstream interceptor
 *       eats the exception, an auth row can survive without a paired
 *       profile. The auth event still went out.</li>
 *   <li><b>Legacy users.</b> Accounts created before the Feign-create
 *       path was wired in. Re-emitting their {@code user.registered}
 *       event (manual replay or backfill job) will heal them through
 *       this listener.</li>
 *   <li><b>Cold-start ordering.</b> User Service started after Auth
 *       Service published the event. {@code auto-offset-reset: earliest}
 *       on the consumer means we still pick the message up later.</li>
 * </ul>
 *
 * <p>The listener is idempotent — if a profile already exists for this
 * {@code authUserId}, we log "already exists" and return. We also skip
 * if the email is already in use by a different (non-deleted) profile
 * to avoid violating the unique constraint on {@code users.email}.
 *
 * <p>The {@link UserRegisteredEvent} carries only {@code email + userName};
 * we split the userName on whitespace to derive a best-effort
 * {@code firstName} / {@code lastName}. The user can always edit later
 * via the Profile page.
 */
@Component
@Slf4j
public class UserRegisteredListener {

    private final UserRepo userRepo;

    public UserRegisteredListener(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @KafkaListener(
            topics = "${app.kafka.auth-topic:auth-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-user-service}-user-registered",
            properties = {
                    "spring.json.value.default.type=" +
                            "com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent"
            }
    )
    @Transactional
    public void onUserRegistered(UserRegisteredEvent e) {
        if (e == null || !"user.registered".equals(e.getEventType())) return;
        if (e.getAuthUserId() == null || e.getAuthUserId().isBlank()) {
            log.warn("user.registered with null authUserId — skipping");
            return;
        }

        // Idempotency: bail early if a profile already exists.
        if (userRepo.findFirstByAuthUserIdAndIsDeletedFalse(e.getAuthUserId()).isPresent()) {
            log.debug("Profile already exists for authUserId={} — skipping create",
                    e.getAuthUserId());
            return;
        }

        // Email conflict: a separate (non-deleted) profile already owns
        // this email. Don't fight it — log and bail. The other row almost
        // certainly belongs to the same human; an admin can repair the
        // authUserId link manually if not.
        if (e.getEmail() != null
                && userRepo.existsByEmailIgnoreCaseAndIsDeletedFalse(e.getEmail())) {
            log.warn("Cannot create profile for authUserId={}: email={} already used by another active row",
                    e.getAuthUserId(), e.getEmail());
            return;
        }

        // Best-effort name split. UserRegisteredEvent only carries userName,
        // so we use that as a fallback for firstName/lastName until the
        // user fills the real values via the profile page.
        String firstName = "";
        String lastName = "";
        if (e.getUserName() != null && !e.getUserName().isBlank()) {
            String[] parts = e.getUserName().trim().split("\\s+", 2);
            firstName = parts[0];
            if (parts.length > 1) lastName = parts[1];
        }
        // firstName has a NOT NULL constraint at the DB layer — fall back
        // to the userName itself, then a placeholder, never to null.
        if (firstName.isBlank()) {
            firstName = e.getUserName() == null || e.getUserName().isBlank()
                    ? "User"
                    : e.getUserName();
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .authUserId(e.getAuthUserId())
                .firstName(firstName)
                .lastName(lastName.isBlank() ? null : lastName)
                .email(e.getEmail())
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .kycStatus("PENDING")
                .preferredLanguage("en")
                .build();
        userRepo.save(user);
        log.info("Created profile for authUserId={} email={} via user.registered self-heal",
                e.getAuthUserId(), e.getEmail());
    }
}
