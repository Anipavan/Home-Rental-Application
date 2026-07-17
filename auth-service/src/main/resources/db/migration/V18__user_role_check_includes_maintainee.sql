-- ─────────────────────────────────────────────────────────────────
--  V18 — Allow MAINTAINEE values in user_details_table.user_role.
--
--  Follow-up to V2 (which added MAINTAINER to the CHECK constraint).
--  Same drop-and-recreate dance because the constraint name
--  {@code ck_user_details_role} was created by V2, and Oracle can't
--  ALTER an existing CHECK constraint's condition in place — we have
--  to drop it and add a new one.
--
--  Idempotent: skips the DROP when the constraint is absent, skips
--  the ADD when a constraint by the same name already exists.
-- ─────────────────────────────────────────────────────────────────

DECLARE
    v_count NUMBER;
BEGIN
    -- Drop V2's constraint if present (may already be absent on a
    -- fresh install that runs V18 before V2 has laid it down — the
    -- Java @Enumerated column just creates a system-generated check
    -- in that case, which we handle next).
    FOR rec IN (
        SELECT c.CONSTRAINT_NAME
          FROM USER_CONS_COLUMNS cc
          JOIN USER_CONSTRAINTS  c  ON c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
         WHERE UPPER(cc.TABLE_NAME)  = 'USER_DETAILS_TABLE'
           AND UPPER(cc.COLUMN_NAME) = 'USER_ROLE'
           AND c.CONSTRAINT_TYPE     = 'C'
           AND DBMS_LOB.INSTR(TO_CLOB(c.SEARCH_CONDITION), 'IS NOT NULL') = 0
    ) LOOP
        BEGIN
            EXECUTE IMMEDIATE
                'ALTER TABLE user_details_table DROP CONSTRAINT '
                || rec.CONSTRAINT_NAME;
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE != -2443 THEN RAISE; END IF;
        END;
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
END;
/
