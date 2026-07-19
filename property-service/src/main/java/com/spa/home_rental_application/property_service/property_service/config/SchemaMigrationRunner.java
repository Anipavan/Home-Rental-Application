package com.spa.home_rental_application.property_service.property_service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent Oracle schema migrations for property-service.
 *
 * <p>Same shape + reasoning as auth-service's SchemaMigrationRunner.
 * Multiple feature batches (browse filters, map view, image gallery
 * cover/order, soft-delete, geo pin) added columns to the existing
 * {@code flats}, {@code registered_buildings}, and {@code propertyimages}
 * tables. Hibernate's {@code ddl-auto=update} is supposed to add them
 * on next boot but doesn't always — the Oracle DEFAULT clause + NOT
 * NULL combo is the usual culprit, and Hibernate's "skip on doubt"
 * behaviour silently bails.
 *
 * <p>Without these columns, the wishlist heart-toggle fails because
 * {@code FlatFavoriteController.add} calls {@code flatService.getflatById(flatId)}
 * which selects every Flat column — a missing column (e.g.
 * {@code description}, {@code deposit_amount}, {@code furnishing_status})
 * raises ORA-00904 and the toggle bubbles up as a 500. Same for
 * saved-searches (it queries the flat catalog as the create predicate
 * cross-check) and building detail pages.
 *
 * <p>Runs at {@link PostConstruct} so the migration is done before
 * any controller can race a query. Catches the predictable Oracle
 * "duplicate column" / "table doesn't exist yet" errors so re-runs
 * + fresh-schema boots both work.
 *
 * <p>Disable via {@code app.schema-migrations.enabled=false} once
 * the team adopts Flyway/Liquibase for real versioned migrations.
 */
@Component
@Slf4j
public class SchemaMigrationRunner {

    /**
     * Each entry: {table, column, type-with-constraint}. Issued as
     * {@code ALTER TABLE %s ADD %s %s} and re-run-safe.
     *
     * <p>Order matters: parent tables before child ones in case any
     * future migration adds an FK constraint. None of the current
     * entries need that, but the convention helps when the list
     * grows.
     */
    private static final List<Migration> MIGRATIONS = List.of(
            // ── flats (browse-filter listing attributes) ──
            new Migration("flats", "furnishing_status", "VARCHAR2(32)"),
            new Migration("flats", "pet_friendly",      "NUMBER(1)"),
            new Migration("flats", "available_from",    "DATE"),
            new Migration("flats", "deposit_amount",    "NUMBER(12,2)"),
            new Migration("flats", "description",       "VARCHAR2(2000)"),
            new Migration("flats", "is_deleted",        "NUMBER(1) DEFAULT 0 NOT NULL"),
            new Migration("flats", "created_at",        "TIMESTAMP"),
            new Migration("flats", "updated_at",        "TIMESTAMP"),
            // Tenant-initiated scheduled vacate (Issue #5). NULL until
            // tenant clicks "Schedule vacate" — daily VacateScheduler
            // sweeps for non-null dates, fires owner warning 10 days
            // before, and performs the actual vacate on the date itself.
            new Migration("flats", "scheduled_vacate_date",  "DATE"),
            new Migration("flats", "vacate_warning_sent_at", "TIMESTAMP"),

            // ── registered_buildings (geo pin + soft-delete + city/state FKs) ──
            new Migration("registered_buildings", "latitude",   "NUMBER(10,6)"),
            new Migration("registered_buildings", "longitude",  "NUMBER(10,6)"),
            new Migration("registered_buildings", "state_id",   "NUMBER(19)"),
            new Migration("registered_buildings", "city_id",    "NUMBER(19)"),
            new Migration("registered_buildings", "is_deleted", "NUMBER(1) DEFAULT 0 NOT NULL"),
            // V14 — Two-facet building. maintainer_user_id is the
            // auth-user who registered this building for society mgmt
            // (NULL for legacy owner-listed rental buildings). Tracking
            // + fast dedup; approval routing still uses society_config.
            new Migration("registered_buildings", "maintainer_user_id", "VARCHAR2(64)"),

            // ── propertyimages (cover + sort_order from gallery feature) ──
            new Migration("propertyimages", "is_cover",   "NUMBER(1) DEFAULT 0 NOT NULL"),
            new Migration("propertyimages", "sort_order", "NUMBER(10) DEFAULT 1000 NOT NULL"),

            // ── building_society_config (V16 — bank-config health flag) ──
            // Non-null bank_config_flagged_at = a tenant reported the
            // society's UPI as broken. Maintainer dashboard renders a
            // warning banner; editing upi_id or payee_name in the bank
            // panel auto-clears both columns (see SocietyServiceImpl).
            new Migration("building_society_config", "bank_config_flagged_at",   "TIMESTAMP"),
            new Migration("building_society_config", "bank_config_flag_reports", "NUMBER(10) DEFAULT 0")
    );

    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * CREATE TABLE statements for entirely-new tables. Hibernate's
     * {@code ddl-auto=update} usually creates these on first boot but
     * has shipped silently-skipped tables in multiple environments;
     * these run as a deterministic backstop. Wrapped in PL/SQL
     * EXCEPTION blocks so re-runs against an already-created table
     * (ORA-00955) are no-ops.
     */
    private static final List<String> CREATE_TABLES = List.of(
            // ── saved_searches (the "Save this search" alerts feature) ──
            "CREATE TABLE saved_searches (" +
            "  id                  VARCHAR2(64) PRIMARY KEY," +
            "  user_id             VARCHAR2(64) NOT NULL," +
            "  name                VARCHAR2(200)," +
            "  city                VARCHAR2(100)," +
            "  bedrooms            NUMBER(10)," +
            "  max_rent            NUMBER(12,2)," +
            "  min_rent            NUMBER(12,2)," +
            "  min_area_sqft       NUMBER(15,2)," +
            "  furnishing_status   VARCHAR2(32)," +
            "  pet_friendly        NUMBER(1)," +
            "  is_active           NUMBER(1) DEFAULT 1 NOT NULL," +
            "  last_matched_at     TIMESTAMP," +
            "  created_at          TIMESTAMP NOT NULL" +
            ")",
            "CREATE INDEX idx_savedsearch_user ON saved_searches (user_id)",
            "CREATE INDEX idx_savedsearch_active ON saved_searches (is_active)",

            // ── flat_favorites (wishlist) — same backstop logic ──
            "CREATE TABLE flat_favorites (" +
            "  id          VARCHAR2(64) PRIMARY KEY," +
            "  user_id     VARCHAR2(64) NOT NULL," +
            "  flat_id     VARCHAR2(64) NOT NULL," +
            "  created_at  TIMESTAMP NOT NULL," +
            "  CONSTRAINT uq_fav_user_flat UNIQUE (user_id, flat_id)" +
            ")",
            "CREATE INDEX idx_fav_user ON flat_favorites (user_id)",
            "CREATE INDEX idx_fav_flat ON flat_favorites (flat_id)",

            // ── flat_society_membership (V15) ──
            // Splits "who lives here for maintenance billing" from
            // flats.tenant_id (which now means rental tenant only).
            // Written by both applyResidentApproval and the owner-side
            // assignFlat flow — the maintainer's dashboard reads this,
            // not tenant_id, when listing residents.
            "CREATE TABLE flat_society_membership (" +
            "  flat_id       VARCHAR2(64) NOT NULL," +
            "  user_id       VARCHAR2(64) NOT NULL," +
            "  joined_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL," +
            "  approved_by   VARCHAR2(64)," +
            "  is_active     NUMBER(1) DEFAULT 1 NOT NULL," +
            "  CONSTRAINT pk_flat_society_membership PRIMARY KEY (flat_id, user_id)" +
            ")",
            "CREATE INDEX idx_fsm_user ON flat_society_membership (user_id)",
            "CREATE INDEX idx_fsm_flat_active ON flat_society_membership (flat_id, is_active)"
    );

    @PostConstruct
    public void run() {
        log.info("property-service SchemaMigrationRunner: applying {} idempotent migration(s)",
                MIGRATIONS.size() + CREATE_TABLES.size());

        // Pass 1 — CREATE TABLE (idempotent: ORA-00955 = "name already
        // used by an existing object" is the expected no-op on re-runs).
        int created = 0;
        int createSkipped = 0;
        for (String ddl : CREATE_TABLES) {
            try {
                jdbc.execute(ddl);
                String snippet = ddl.length() > 80 ? ddl.substring(0, 80) + "…" : ddl;
                log.info("Executed: {}", snippet);
                created++;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("ORA-00955")) {
                    log.debug("Already exists: {}", ddl.split("\\s+")[2]);
                    createSkipped++;
                    continue;
                }
                log.error("CREATE TABLE failed: {} — {}",
                        ddl.split("\\s+")[2], msg);
            }
        }
        log.info("CREATE pass: created={}, skipped={}", created, createSkipped);

        // Pass 2 — ALTER TABLE ADD COLUMN
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
                // Predictable / safe errors:
                //   ORA-01430: column being added already exists in table
                //   ORA-00955: name already used by an existing object
                //   ORA-00942: table or view does not exist (fresh DB;
                //              Hibernate creates the table moments later
                //              with the column already on it)
                //   ORA-01758: NOT NULL column can't be added if the
                //              column-definition is missing a DEFAULT —
                //              the columnDefinition strings above all
                //              include DEFAULT, so we shouldn't hit this
                //              for our own rows, but tolerating it makes
                //              re-runs idempotent.
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
        log.info("property-service SchemaMigrationRunner: complete (added={}, skipped={})", added, skipped);

        // Pass 3 — CHECK-constraint widenings that Flyway migrations
        // handle but need mirroring for the Flyway-off prod path.
        applyClaimsRoleCheckConstraint();
    }

    /**
     * Mirror of V8's CHECK-constraint widening on
     * {@code membership_claims.requested_role}. V6 created the table
     * with {@code CHECK (requested_role IN ('MAINTAINER', 'RESIDENT'))};
     * V8 dropped that and added a version including {@code FLAT_OWNER}.
     * Prod runs Flyway-disabled, so without this mirror a new
     * FLAT_OWNER MembershipClaim trips ORA-02290
     * "check constraint CHK_CLAIMS_ROLE violated".
     *
     * <p>Idempotent — skips when a constraint already permitting
     * FLAT_OWNER exists.
     */
    private void applyClaimsRoleCheckConstraint() {
        try {
            jdbc.execute(
                    "DECLARE " +
                    "  v_search VARCHAR2(4000); " +
                    "  v_already_ok NUMBER := 0; " +
                    "BEGIN " +
                    "  FOR rec IN ( " +
                    "    SELECT c.constraint_name, c.search_condition " +
                    "      FROM user_cons_columns cc " +
                    "      JOIN user_constraints  c ON c.constraint_name = cc.constraint_name " +
                    "     WHERE UPPER(cc.table_name)  = 'MEMBERSHIP_CLAIMS' " +
                    "       AND UPPER(cc.column_name) = 'REQUESTED_ROLE' " +
                    "       AND c.constraint_type     = 'C' " +
                    "  ) LOOP " +
                    "    v_search := DBMS_LOB.SUBSTR(TO_CLOB(rec.search_condition), 4000, 1); " +
                    "    IF v_search IS NOT NULL AND INSTR(UPPER(v_search), 'FLAT_OWNER') > 0 THEN " +
                    "      v_already_ok := 1; " +
                    "    ELSIF v_search IS NULL OR INSTR(UPPER(v_search), 'IS NOT NULL') = 0 THEN " +
                    "      BEGIN " +
                    "        EXECUTE IMMEDIATE 'ALTER TABLE membership_claims DROP CONSTRAINT ' " +
                    "          || rec.constraint_name; " +
                    "      EXCEPTION WHEN OTHERS THEN " +
                    "        IF SQLCODE != -2443 THEN RAISE; END IF; " +
                    "      END; " +
                    "    END IF; " +
                    "  END LOOP; " +
                    "  IF v_already_ok = 0 THEN " +
                    "    BEGIN " +
                    "      EXECUTE IMMEDIATE " +
                    "        'ALTER TABLE membership_claims ADD CONSTRAINT chk_claims_role " +
                    "         CHECK (requested_role IN " +
                    "           (''MAINTAINER'',''RESIDENT'',''FLAT_OWNER''))'; " +
                    "    EXCEPTION WHEN OTHERS THEN " +
                    "      IF SQLCODE NOT IN (-955, -2264) THEN RAISE; END IF; " +
                    "    END; " +
                    "  END IF; " +
                    "END;");
            log.info("CHECK constraint on membership_claims.requested_role refreshed to permit FLAT_OWNER (chk_claims_role)");
        } catch (Exception ex) {
            log.warn("membership_claims.requested_role CHECK refresh skipped: {}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage().lines().findFirst().orElse(""));
        }
    }

    private record Migration(String table, String column, String type) {}
}
