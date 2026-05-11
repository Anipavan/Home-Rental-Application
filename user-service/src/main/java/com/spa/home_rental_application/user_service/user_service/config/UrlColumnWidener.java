package com.spa.home_rental_application.user_service.user_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time defensive ALTER for {@code users.profile_picture_url} and
 * {@code users.id_proof_url} — widens them to VARCHAR2(4000).
 *
 * <p>Why this exists despite the entity already declaring
 * {@code length = 4000}: Hibernate {@code ddl-auto=update} is reliable
 * for ADDING new columns but historically inconsistent about altering
 * existing column lengths on Oracle (often skipping the ALTER, sometimes
 * skipping silently after a single failure). On a deployment that
 * started life with VARCHAR2(255) columns, leaving the widening to
 * Hibernate alone risks the URL-overflow bug persisting — surfacing as
 * the catch-all "Unexpected error occurred" toast when a tenant
 * uploads a profile photo.
 *
 * <p>We probe the column metadata first and only issue the ALTER when
 * it's actually too narrow, so the runner is a no-op on fresh schemas
 * and on schemas already widened (whether by Hibernate or a previous
 * run of this class).
 */
@Component
@Slf4j
public class UrlColumnWidener {

    private static final int TARGET_LENGTH = 4000;

    private final JdbcTemplate jdbc;

    public UrlColumnWidener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void widenIfNeeded() {
        widenColumn("PROFILE_PICTURE_URL");
        widenColumn("ID_PROOF_URL");
    }

    private void widenColumn(String columnName) {
        try {
            // Oracle stores column metadata in upper-case in USER_TAB_COLUMNS.
            // CHAR_LENGTH gives the declared character length regardless of
            // byte-vs-char semantics.
            Integer current = jdbc.queryForObject(
                    "SELECT CHAR_LENGTH FROM USER_TAB_COLUMNS " +
                            "WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = ?",
                    Integer.class,
                    columnName);

            if (current == null) {
                log.debug("UrlColumnWidener: column USERS.{} not found yet — Hibernate will create it at length {}",
                        columnName, TARGET_LENGTH);
                return;
            }
            if (current >= TARGET_LENGTH) {
                log.debug("UrlColumnWidener: USERS.{} already {} chars — no ALTER needed",
                        columnName, current);
                return;
            }

            String sql = String.format(
                    "ALTER TABLE USERS MODIFY (%s VARCHAR2(%d))", columnName, TARGET_LENGTH);
            log.info("UrlColumnWidener: widening USERS.{} from {} → {}",
                    columnName, current, TARGET_LENGTH);
            jdbc.execute(sql);
        } catch (Exception ex) {
            // Don't fail startup. The entity-level @Column(length=4000)
            // will at least apply on fresh deployments; existing ones
            // can be hand-migrated if this best-effort path doesn't take.
            log.warn("UrlColumnWidener: could not widen USERS.{} (non-fatal): {}",
                    columnName, ex.getMessage());
        }
    }
}
