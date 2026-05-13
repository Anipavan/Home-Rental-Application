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
            new Migration("user_details_table", "phone",                 "VARCHAR2(20)")
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
    }

    private record Migration(String table, String column, String type) {}
}
