-- ─────────────────────────────────────────────────────────────────
--  V2 — Allow MAINTAINER values in user_details_table.user_role.
--
--  Background: when auth-service first created the table with
--  Hibernate ddl-auto=update, the @Enumerated(EnumType.STRING) column
--  picked up an auto-generated CHECK constraint listing only the
--  roles that existed at the time:
--    user_role IN ('ADMIN','OWNER','TENANT','TENENT')
--  The constraint name was system-generated (SYS_C<numbers>) so we
--  can't ALTER it by name across environments.
--
--  Symptom this fixes:
--    POST /auth/internal/users/{id}/promote-to-maintainer →
--    ORA-02290: check constraint (HRA_APP.SYS_C00100xx) violated
--    when Hibernate tried to UPDATE user_role from 'TENANT' to
--    'MAINTAINER'.
--
--  Strategy:
--    1. Iterate every CHECK constraint touching user_role.
--    2. Skip Oracle's internal NOT NULL check (those store the
--       condition as '"USER_ROLE" IS NOT NULL').
--    3. Drop the rest — they're all stale enum checks.
--    4. Add the replacement under a stable, hand-chosen name
--       so any future role addition is a one-liner ALTER on a
--       known constraint name instead of another SYS_C hunt.
--
--  Safe to re-run: the PL/SQL block is idempotent. If the
--  ck_user_details_role constraint already exists (re-deploys, fresh
--  installs), we just skip the ADD.
-- ─────────────────────────────────────────────────────────────────

DECLARE
    v_count   NUMBER;
    v_search  VARCHAR2(4000);
BEGIN
    -- Step 1+2+3: drop every non-NOT-NULL check on user_role.
    FOR rec IN (
        SELECT c.CONSTRAINT_NAME, c.SEARCH_CONDITION
          FROM USER_CONS_COLUMNS cc
          JOIN USER_CONSTRAINTS  c  ON c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
         WHERE UPPER(cc.TABLE_NAME)   = 'USER_DETAILS_TABLE'
           AND UPPER(cc.COLUMN_NAME)  = 'USER_ROLE'
           AND c.CONSTRAINT_TYPE      = 'C'
           AND c.CONSTRAINT_NAME     <> 'CK_USER_DETAILS_ROLE'
    ) LOOP
        -- SEARCH_CONDITION is LONG; coerce to a VARCHAR2 for the
        -- IS NOT NULL filter so the NOT-NULL constraint is preserved.
        v_search := DBMS_LOB.SUBSTR(TO_CLOB(rec.SEARCH_CONDITION), 4000, 1);
        IF v_search IS NULL OR INSTR(UPPER(v_search), 'IS NOT NULL') = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE
                    'ALTER TABLE user_details_table DROP CONSTRAINT '
                    || rec.CONSTRAINT_NAME;
            EXCEPTION
                WHEN OTHERS THEN
                    -- ORA-02443 'cannot drop constraint - nonexistent
                    -- constraint' is fine (concurrent re-run); rethrow
                    -- everything else.
                    IF SQLCODE != -2443 THEN RAISE; END IF;
            END;
        END IF;
    END LOOP;

    -- Step 4: add the named replacement if not already there.
    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'CK_USER_DETAILS_ROLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE user_details_table '
         || 'ADD CONSTRAINT ck_user_details_role '
         || 'CHECK (user_role IN '
         ||   '(''ADMIN'',''OWNER'',''MAINTAINER'',''TENANT'',''TENENT''))';
    END IF;
END;
/
