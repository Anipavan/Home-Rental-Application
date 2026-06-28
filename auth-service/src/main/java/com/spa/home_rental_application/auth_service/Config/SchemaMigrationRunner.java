package com.spa.home_rental_application.auth_service.Config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent Oracle schema migrations for auth-service.
 *
 * <p>The CRITICAL / HIGH security batches added new columns to
 * {@code user_details_table} (H4 account-lockout, H3 token-revoke
 * watermark) and {@code refresh_tokens} (H5 IP/UA binding). On a
 * fresh schema, Hibernate's {@code ddl-auto=update} creates the
 * tables with the new columns out of the box. But on a database
 * that pre-dates those commits — i.e. every existing local dev
 * setup the team's already been using — the columns are missing
 * and the login pre-flight check (which now SELECTs
 * {@code failed_login_attempts}) throws ORA-00904.
 *
 * <p>Hibernate's update mode SHOULD add the columns automatically.
 * In practice it doesn't always — column-definition mismatches,
 * Oracle DEFAULT-clause quirks, and update-mode's "skip on doubt"
 * default all conspire. This runner is the deterministic fallback:
 * it issues the ALTER TABLE statements directly and swallows the
 * "duplicate column" error (ORA-01430 / ORA-01442) on re-runs, so
 * it's safe to keep enabled forever.
 *
 * <p>Runs at {@link PostConstruct} time so it's done before any
 * @KafkaListener or REST handler can race a query against a missing
 * column.
 *
 * <p>Disable via {@code app.schema-migrations.enabled=false} if a
 * future deploy uses Flyway/Liquibase instead.
 */
@Component
@Slf4j
public class SchemaMigrationRunner {

    /**
     * Each entry: {table, column, type-with-constraint}. Issued as
     * {@code ALTER TABLE %s ADD %s %s} and re-run-safe — duplicate
     * column errors are caught and logged at DEBUG.
     */
    private static final List<Migration> MIGRATIONS = List.of(
            // H4 — account-lockout counters
            new Migration("user_details_table", "failed_login_attempts", "NUMBER(10) DEFAULT 0 NOT NULL"),
            new Migration("user_details_table", "locked_until",          "TIMESTAMP"),
            // H3 — per-user "tokens issued before this point are dead" watermark
            new Migration("user_details_table", "tokens_revoked_before", "TIMESTAMP"),
            // H5 — refresh-token fingerprint columns
            new Migration("refresh_tokens",     "ip_address",            "VARCHAR2(64)"),
            new Migration("refresh_tokens",     "user_agent_hash",       "VARCHAR2(64)"),
            // Phone uniqueness at registration. Nullable on existing
            // rows (so legacy users without a phone don't violate
            // anything); Hibernate creates the matching unique index
            // `idx_user_details_phone` from the @Index annotation on
            // UserDetails. New registrations populate this column
            // with the E.164-normalised number; AuthServiceImpl
            // rejects duplicates with DuplicateUserException → HTTP 409.
            new Migration("user_details_table", "phone",                 "VARCHAR2(20)"),
            // V5 — reason carried alongside enabled=false so login()
            // can branch on it.
            new Migration("user_details_table", "disable_reason",        "VARCHAR2(60)"),
            // V15 — maintainer-payment soft gate. trial clock + skip
            // counters + paid-at watermark. NULL paid_at = trialling
            // or past the skip window; non-null = either paid or
            // grandfathered. See the V15 migration header for the full
            // state machine.
            new Migration("user_details_table", "payment_trial_started_at", "TIMESTAMP"),
            new Migration("user_details_table", "payment_skip_count",       "NUMBER(2) DEFAULT 0 NOT NULL"),
            new Migration("user_details_table", "payment_last_skip_at",     "TIMESTAMP"),
            new Migration("user_details_table", "payment_paid_at",          "TIMESTAMP"),
            // V16 — email-verification gate. Boolean column starts 0;
            // backfill below flips every existing row to 1 (grandfather).
            // Token table is created by Hibernate from the
            // EmailVerificationToken @Entity.
            new Migration("user_details_table", "email_verified",          "NUMBER(1) DEFAULT 0 NOT NULL")
    );

    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void run() {
        log.info("SchemaMigrationRunner: applying {} idempotent migration(s)", MIGRATIONS.size());
        int added = 0;
        int skipped = 0;
        for (Migration m : MIGRATIONS) {
            String ddl = String.format("ALTER TABLE %s ADD %s %s",
                    m.table, m.column, m.type);
            try {
                jdbc.execute(ddl);
                log.info("Added column {}.{} ({})", m.table, m.column, m.type);
                added++;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                // ORA-01430: column being added already exists in table
                // ORA-00955: name is already used by an existing object
                // ORA-00942: table or view does not exist (fresh DB —
                //            Hibernate creates the table milliseconds
                //            later, with the column already on it; safe
                //            to skip here)
                if (msg.contains("ORA-01430") || msg.contains("ORA-00955")
                        || msg.contains("ORA-00942")) {
                    log.debug("Skipping {}.{}: {}", m.table, m.column,
                            msg.lines().findFirst().orElse(msg));
                    skipped++;
                    continue;
                }
                // Anything else is genuinely surprising — log loudly
                // but don't crash startup; the next-best outcome is
                // services still come up and the operator investigates.
                log.error("Unexpected error adding {}.{}: {}", m.table, m.column, msg, ex);
            }
        }
        log.info("SchemaMigrationRunner: complete (added={}, skipped={})", added, skipped);

        // ── One-time data backfills paired with the columns above. ─
        // These are guarded by NOT-EXISTS / IS-NULL clauses so they're
        // safe to re-run on every boot — the second run finds zero
        // rows to touch and exits without writes. Mirrors what the
        // V14 + V15 Flyway migrations do, for the deploys that have
        // Flyway disabled (compose.bootstrap.yml).
        backfillGrandfatherPaidAt();
        backfillSeedSystemSettings();
        backfillGrandfatherEmailVerified();
        backfillSeedEmailVerificationToggle();
        backfillUserRolesJoinTable();
    }

    /**
     * V15 — every existing user gets payment_paid_at =
     * record_created_date so they're permanently in the PAID state.
     * Idempotent: WHERE payment_paid_at IS NULL means a second pass
     * touches zero rows. Also clears any V5-era
     * disable_reason='REGISTRATION_PAYMENT_PENDING' rows since the
     * hard-paywall path is being rolled back.
     */
    private void backfillGrandfatherPaidAt() {
        try {
            int rows = jdbc.update(
                    "UPDATE user_details_table " +
                    "   SET payment_paid_at = record_created_date, " +
                    "       payment_trial_started_at = record_created_date " +
                    " WHERE payment_paid_at IS NULL");
            if (rows > 0) {
                log.info("Grandfathered {} existing user row(s): payment_paid_at = record_created_date", rows);
            } else {
                log.debug("Grandfather UPDATE matched 0 rows (already done)");
            }
        } catch (Exception ex) {
            // Most likely cause: payment_paid_at column doesn't exist
            // yet (Hibernate hasn't created the table; first boot).
            // Hibernate's ddl-auto=update will add it; next boot
            // backfills.
            log.warn("Grandfather UPDATE skipped — column not yet present? {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }

        try {
            int rows = jdbc.update(
                    "UPDATE user_details_table " +
                    "   SET disable_reason = NULL, enabled = 1 " +
                    " WHERE disable_reason = 'REGISTRATION_PAYMENT_PENDING'");
            if (rows > 0) {
                log.info("Reversed V5 paywall on {} row(s) — disable_reason cleared, enabled=1", rows);
            }
        } catch (Exception ex) {
            log.warn("V5 paywall reversal skipped: {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }
    }

    /**
     * V17 — backfill the user_roles join table from the legacy
     * single-role column. Hibernate's ddl-auto=update creates the
     * empty join table on first boot from the
     * {@code @ElementCollection} on UserDetails. This method then
     * copies every existing (user_id, user_role) pair into it,
     * guarded by NOT EXISTS so re-runs are no-ops.
     *
     * <p>Order matters: this MUST run after Hibernate's CREATE TABLE
     * pass, which is true here because SchemaMigrationRunner is
     * @PostConstruct-bound (fires after the EntityManagerFactory is
     * ready) but the actual INSERT is wrapped in a try/catch so a
     * missing table on the very first boot just logs a warning and
     * the next boot completes the backfill.
     */
    private void backfillUserRolesJoinTable() {
        try {
            int rows = jdbc.update(
                    "INSERT INTO user_roles (user_id, role) " +
                    "SELECT u.id, u.user_role " +
                    "  FROM user_details_table u " +
                    " WHERE u.user_role IS NOT NULL " +
                    "   AND NOT EXISTS (" +
                    "       SELECT 1 FROM user_roles r " +
                    "        WHERE r.user_id = u.id AND r.role = u.user_role)");
            if (rows > 0) {
                log.info("Backfilled {} row(s) into user_roles from legacy user_role column", rows);
            } else {
                log.debug("user_roles backfill matched 0 rows (already done)");
            }
        } catch (Exception ex) {
            // Most likely cause: user_roles table not present yet
            // (Hibernate hasn't created it on first boot). Next boot
            // will complete the backfill.
            log.warn("user_roles backfill skipped: {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }
    }

    /**
     * V16 — grandfather every existing user as email_verified=1 so the
     * later toggle flip doesn't lock them out. Idempotent: subsequent
     * runs match zero rows (every row already at 1).
     */
    private void backfillGrandfatherEmailVerified() {
        try {
            int rows = jdbc.update(
                    "UPDATE user_details_table " +
                    "   SET email_verified = 1 " +
                    " WHERE email_verified = 0");
            if (rows > 0) {
                log.info("Grandfathered {} existing user row(s): email_verified = 1", rows);
            } else {
                log.debug("Grandfather email_verified UPDATE matched 0 rows (already done)");
            }
        } catch (Exception ex) {
            log.warn("Grandfather email_verified UPDATE skipped — column not yet present? {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }
    }

    /**
     * V16 — seed the email_verification_required toggle as 'false' so
     * the gate is dormant on first deploy. Admin flips it from
     * /admin/settings when ready. Idempotent via NOT EXISTS.
     */
    private void backfillSeedEmailVerificationToggle() {
        try {
            int rows = jdbc.update(
                    "INSERT INTO system_settings (setting_key, value, updated_at) " +
                    "SELECT 'email_verification_required', 'false', SYSTIMESTAMP FROM dual " +
                    " WHERE NOT EXISTS (" +
                    "       SELECT 1 FROM system_settings WHERE setting_key = 'email_verification_required')");
            if (rows > 0) {
                log.info("Seeded default system_settings: email_verification_required=false");
            }
        } catch (Exception ex) {
            log.warn("email_verification_required seed skipped: {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }
    }

    /**
     * V14 — seed the default {@code maintainer_payment_enabled=false}
     * row if the system_settings table exists and the row is missing.
     * Idempotent via NOT EXISTS. The table itself is created by
     * Hibernate from the {@code SystemSetting} entity.
     */
    private void backfillSeedSystemSettings() {
        try {
            int rows = jdbc.update(
                    "INSERT INTO system_settings (setting_key, value, updated_at) " +
                    "SELECT 'maintainer_payment_enabled', 'false', SYSTIMESTAMP FROM dual " +
                    " WHERE NOT EXISTS (" +
                    "       SELECT 1 FROM system_settings WHERE setting_key = 'maintainer_payment_enabled')");
            if (rows > 0) {
                log.info("Seeded default system_settings: maintainer_payment_enabled=false");
            }
        } catch (Exception ex) {
            // Table not present yet (Hibernate hasn't run, first
            // boot) — next boot will seed.
            log.warn("system_settings seed skipped: {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }
    }

    private record Migration(String table, String column, String type) {}
}
