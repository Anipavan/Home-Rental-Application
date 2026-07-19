-- ─────────────────────────────────────────────────────────────────
--  V19 — Allow MAINTAINEE values in TWO role CHECK constraints.
--
--  Two things need fixing:
--
--   1. {@code user_details_table.user_role} — V18 tried to update
--      this constraint but its DBMS_LOB.INSTR(TO_CLOB(search_condition))
--      call in the WHERE clause fails on Oracle 23c ("expression is of
--      data type LONG"). The migration silently no-op'd. Re-do with
--      V2's proven loop-then-filter pattern.
--
--   2. {@code user_roles.role} — new in V17 (the multi-role join
--      table). Hibernate auto-generated a CHECK constraint listing
--      the enum values at CREATE time. Adding MAINTAINEE to the
--      enum bumped the java side but the constraint stayed frozen,
--      so the first INSERT of role='MAINTAINEE' via
--      grantMaintaineeRole hits ORA-02290.
--
--  Pattern below mirrors V2 exactly — SELECT every non-NOT-NULL
--  CHECK on the target (table, column), coerce SEARCH_CONDITION to
--  a VARCHAR2 slice inside the loop, then drop each. Idempotent —
--  a second run against a table that already has only the named
--  ck_* constraint matches zero rows.
-- ─────────────────────────────────────────────────────────────────

DECLARE
    v_search VARCHAR2(4000);
    v_count  NUMBER;
BEGIN
    -- ---- (1) user_details_table.user_role ---------------------------
    FOR rec IN (
        SELECT c.CONSTRAINT_NAME, c.SEARCH_CONDITION
          FROM USER_CONS_COLUMNS cc
          JOIN USER_CONSTRAINTS  c ON c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
         WHERE UPPER(cc.TABLE_NAME)   = 'USER_DETAILS_TABLE'
           AND UPPER(cc.COLUMN_NAME)  = 'USER_ROLE'
           AND c.CONSTRAINT_TYPE      = 'C'
           AND c.CONSTRAINT_NAME     <> 'CK_USER_DETAILS_ROLE'
    ) LOOP
        v_search := DBMS_LOB.SUBSTR(TO_CLOB(rec.SEARCH_CONDITION), 4000, 1);
        IF v_search IS NULL OR INSTR(UPPER(v_search), 'IS NOT NULL') = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE
                    'ALTER TABLE user_details_table DROP CONSTRAINT '
                    || rec.CONSTRAINT_NAME;
            EXCEPTION WHEN OTHERS THEN
                IF SQLCODE != -2443 THEN RAISE; END IF;
            END;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'CK_USER_DETAILS_ROLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE user_details_table '
         || 'ADD CONSTRAINT ck_user_details_role '
         || 'CHECK (user_role IN '
         ||   '(''ADMIN'',''OWNER'',''MAINTAINER'',''MAINTAINEE'','
         ||    '''TENANT'',''TENENT''))';
    END IF;

    -- ---- (2) user_roles.role (V17 join table) -----------------------
    FOR rec IN (
        SELECT c.CONSTRAINT_NAME, c.SEARCH_CONDITION
          FROM USER_CONS_COLUMNS cc
          JOIN USER_CONSTRAINTS  c ON c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
         WHERE UPPER(cc.TABLE_NAME)   = 'USER_ROLES'
           AND UPPER(cc.COLUMN_NAME)  = 'ROLE'
           AND c.CONSTRAINT_TYPE      = 'C'
           AND c.CONSTRAINT_NAME     <> 'CK_USER_ROLES_ROLE'
    ) LOOP
        v_search := DBMS_LOB.SUBSTR(TO_CLOB(rec.SEARCH_CONDITION), 4000, 1);
        IF v_search IS NULL OR INSTR(UPPER(v_search), 'IS NOT NULL') = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE
                    'ALTER TABLE user_roles DROP CONSTRAINT '
                    || rec.CONSTRAINT_NAME;
            EXCEPTION WHEN OTHERS THEN
                IF SQLCODE != -2443 THEN RAISE; END IF;
            END;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'CK_USER_ROLES_ROLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE user_roles '
         || 'ADD CONSTRAINT ck_user_roles_role '
         || 'CHECK (role IN '
         ||   '(''ADMIN'',''OWNER'',''MAINTAINER'',''MAINTAINEE'','
         ||    '''TENANT'',''TENENT''))';
    END IF;
END;
/
