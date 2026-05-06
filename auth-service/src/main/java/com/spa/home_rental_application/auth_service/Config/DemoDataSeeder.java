package com.spa.home_rental_application.auth_service.Config;

import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.enums.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Seeds 8 demo accounts on first boot. Idempotent -- each user is
 * inserted only if a row with that username doesn't already exist, so
 * running it twice is safe and partial migrations are completable.
 *
 * Default password for every seeded account: "Password1".
 *
 * Insertion order is fixed so on a FRESH database the auto-generated
 * Long IDs are predictable:
 *   admin           id=1
 *   owner_alice     id=2
 *   owner_bob       id=3
 *   tenant_chris    id=4
 *   tenant_dana     id=5
 *   tenant_eli      id=6
 *   tenant_fran     id=7
 *   tenant_gabe     id=8
 *
 * The property-service demo seeder relies on owner_alice=2, owner_bob=3.
 * If your DB already has users, those IDs may shift -- run the seeder
 * against a fresh schema for the demo data to line up.
 *
 * Disable by setting `app.demo-seed.enabled=false`.
 */
@Component
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "Password1";

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(UserRepository userRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (System.getProperty("app.demo-seed.enabled", "true").equalsIgnoreCase("false")
                || "false".equalsIgnoreCase(System.getenv("APP_DEMO_SEED_ENABLED"))) {
            log.info("DemoDataSeeder: disabled via app.demo-seed.enabled=false");
            return;
        }

        List<Seed> seeds = List.of(
                new Seed("admin",        "admin@homerental.local",   Roles.ADMIN),
                new Seed("owner_alice",  "alice@homerental.local",   Roles.OWNER),
                new Seed("owner_bob",    "bob@homerental.local",     Roles.OWNER),
                new Seed("tenant_chris", "chris@homerental.local",   Roles.TENANT),
                new Seed("tenant_dana",  "dana@homerental.local",    Roles.TENANT),
                new Seed("tenant_eli",   "eli@homerental.local",     Roles.TENANT),
                new Seed("tenant_fran",  "fran@homerental.local",    Roles.TENANT),
                new Seed("tenant_gabe",  "gabe@homerental.local",    Roles.TENANT)
        );

        Instant now = Instant.now();
        int inserted = 0;
        for (Seed s : seeds) {
            // Idempotent: skip individuals that already exist (matches by username
            // OR email -- the entity has unique indexes on both).
            if (userRepo.findByUserName(s.userName).isPresent()) {
                log.debug("DemoDataSeeder: '{}' already exists, skipping", s.userName);
                continue;
            }
            UserDetails u = UserDetails.builder()
                    .userName(s.userName)
                    .userPassword(encoder.encode(DEFAULT_PASSWORD))
                    .email(s.email)
                    .userRole(s.role)
                    .enabled(true)
                    .accountNonLocked(true)
                    .recordCreatedDate(now)
                    .recodeUpdatedDate(now)
                    .build();
            UserDetails saved = userRepo.save(u);
            log.info("DemoDataSeeder: + {} ({}) -- id={} email={}",
                    s.userName, s.role, saved.getId(), s.email);
            inserted++;
        }

        if (inserted == 0) {
            log.info("DemoDataSeeder: nothing to do -- all 8 demo users already exist.");
        } else {
            log.info("DemoDataSeeder: inserted {} demo user(s). Default password = '{}'",
                    inserted, DEFAULT_PASSWORD);
            log.info("");
            log.info("=========================================================");
            log.info("DEMO ACCOUNTS  (sign in at http://localhost:4200/login)");
            log.info("=========================================================");
            log.info("  admin         / Password1   (ADMIN)");
            log.info("  owner_alice   / Password1   (OWNER, ~id=2)");
            log.info("  owner_bob     / Password1   (OWNER, ~id=3)");
            log.info("  tenant_chris  / Password1   (TENANT, ~id=4)");
            log.info("  tenant_dana   / Password1   (TENANT, ~id=5)");
            log.info("  tenant_eli    / Password1   (TENANT, ~id=6)");
            log.info("  tenant_fran   / Password1   (TENANT, ~id=7)");
            log.info("  tenant_gabe   / Password1   (TENANT, ~id=8)");
            log.info("=========================================================");
        }
    }

    private record Seed(String userName, String email, Roles role) {}
}
