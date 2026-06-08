-- ─────────────────────────────────────────────────────────────────
--  V9 — Fix V7's requires_dual_approval column type.
--
--  V7 created the column as NUMBER(1) DEFAULT 0 NOT NULL, mirroring
--  the existing NUMBER(1) "boolean" convention used elsewhere in the
--  schema (flat.is_occupied, flat.is_deleted, etc.). On Oracle 23 +
--  Hibernate 6.6, the strict schema validator now expects Java
--  Boolean fields to be mapped to the native BOOLEAN type — the
--  legacy NUMBER(1) convention fails validation with:
--
--     Schema-validation: wrong column type encountered in column
--     [requires_dual_approval] in table [membership_claims];
--     found [number (Types#NUMERIC)], but expecting [boolean]
--
--  This migration converts the column in-place: add a BOOLEAN twin,
--  copy values, drop the NUMBER column, rename the twin. Safe to
--  re-run — the conversion only runs when the column is still NUMBER.
--
--  The existing legacy Boolean fields (flat.is_occupied etc.) work
--  because they were registered with Hibernate before strict
--  validation kicked in; new Boolean columns added under the strict
--  validator must use BOOLEAN from day one.
-- ─────────────────────────────────────────────────────────────────

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name = 'MEMBERSHIP_CLAIMS'
       AND column_name = 'REQUIRES_DUAL_APPROVAL'
       AND data_type = 'NUMBER';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE membership_claims '
         || 'ADD requires_dual_approval_tmp BOOLEAN DEFAULT FALSE NOT NULL';
        EXECUTE IMMEDIATE
            'UPDATE membership_claims SET requires_dual_approval_tmp = '
         || '(CASE WHEN requires_dual_approval = 1 THEN TRUE ELSE FALSE END)';
        EXECUTE IMMEDIATE 'ALTER TABLE membership_claims DROP COLUMN requires_dual_approval';
        EXECUTE IMMEDIATE
            'ALTER TABLE membership_claims '
         || 'RENAME COLUMN requires_dual_approval_tmp TO requires_dual_approval';
    END IF;
END;
/
COMMIT;
