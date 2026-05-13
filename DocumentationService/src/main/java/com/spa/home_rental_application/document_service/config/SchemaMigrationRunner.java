package com.spa.home_rental_application.document_service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent Oracle schema migrations for document-service. Same
 * pattern as the property-service / auth-service runners: each entry
 * is {table, column, type-with-default}; predictable Oracle errors
 * (duplicate column / table-not-yet-created) are swallowed so re-runs
 * are safe.
 *
 * <p>Issue #9 added the owner approval workflow — new columns
 * (verification_status, rejection_reason, decided_by, decided_at)
 * on the documents table. Hibernate's ddl-auto=update SHOULD add
 * them on first boot, but the NOT NULL + DEFAULT combo on
 * verification_status is the exact case where ddl-auto often
 * silently bails (ORA-01758 trap). This runner is the deterministic
 * fallback.
 */
@Component
@Slf4j
public class SchemaMigrationRunner {

    private static final List<Migration> MIGRATIONS = List.of(
            // Issue #9 — owner approval workflow on Document
            new Migration("documents", "verification_status",
                    "VARCHAR2(32) DEFAULT 'PENDING' NOT NULL"),
            new Migration("documents", "rejection_reason",  "VARCHAR2(500)"),
            new Migration("documents", "decided_by",        "VARCHAR2(64)"),
            new Migration("documents", "decided_at",        "TIMESTAMP")
    );

    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void run() {
        log.info("document-service SchemaMigrationRunner: applying {} idempotent migration(s)",
                MIGRATIONS.size());
        int added = 0;
        int skipped = 0;
        for (Migration m : MIGRATIONS) {
            String ddl = String.format("ALTER TABLE %s ADD %s %s", m.table, m.column, m.type);
            try {
                jdbc.execute(ddl);
                log.info("Added column {}.{} ({})", m.table, m.column, m.type);
                added++;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                // ORA-01430: column being added already exists
                // ORA-00955: name already used by an existing object
                // ORA-00942: table or view does not exist (Hibernate
                //            creates it milliseconds later WITH the
                //            column already on it; safe to skip here)
                // ORA-01758: NOT NULL column needs a DEFAULT — our
                //            verification_status migration includes
                //            DEFAULT 'PENDING' so we shouldn't hit it,
                //            but tolerating it makes re-runs idempotent.
                if (msg.contains("ORA-01430") || msg.contains("ORA-00955")
                        || msg.contains("ORA-00942") || msg.contains("ORA-01758")) {
                    log.debug("Skipping {}.{}: {}", m.table, m.column,
                            msg.lines().findFirst().orElse(msg));
                    skipped++;
                    continue;
                }
                log.error("Unexpected error adding {}.{}: {}", m.table, m.column, msg, ex);
            }
        }
        log.info("document-service SchemaMigrationRunner: complete (added={}, skipped={})",
                added, skipped);
    }

    private record Migration(String table, String column, String type) {}
}
