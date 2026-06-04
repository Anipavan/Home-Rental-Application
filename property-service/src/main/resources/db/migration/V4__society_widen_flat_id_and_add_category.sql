-- ─────────────────────────────────────────────────────────────────
--  V4 — Society schema widening + per-flat charge category.
--
--  Three things in one migration:
--    1. Widen flat_id from VARCHAR2(36) to VARCHAR2(64) on the
--       society tables. Actual flat IDs on prod are FLT-<uuid>
--       (40 chars); the V3 36-char limit was rejecting INSERTs
--       with ORA-12899 and the maintainer's "Set amount" was
--       500-ing for that reason.
--    2. Widen building_id to VARCHAR2(64) on every society table
--       for symmetry — today's IDs are bare UUIDs (36 chars),
--       cheap insurance against a future BLD-<uuid> rename.
--    3. Add a `category` column to maintenance_collection so
--       maintainers can tag each per-flat charge as Water bill /
--       Maintenance / Gas / Electricity / Common-area share /
--       Other (MaintenanceCategory.java enum).
--
--  GOTCHA — ORA-30556.
--  ────────────────────
--  V3 created indexes with DESC sort keys
--  (idx_collection_building_month, idx_expense_building_month) +
--  one regular index (idx_flat_dues_building). Oracle treats DESC
--  indexes as function-based, and ALTER TABLE MODIFY on any
--  column backing a function-based index throws ORA-30556 ("either
--  functional or bitmap join index is defined on the column to be
--  modified"). That's exactly what blew up the FIRST V4 attempt.
--
--  The fix is to DROP the affected indexes BEFORE the MODIFY,
--  then RECREATE them AFTER — identical definitions, so nothing
--  else changes. Everything is wrapped in idempotent
--  EXCEPTION blocks so this migration is safe to re-run on
--  partially-applied state (the bug-fix scenario).
-- ─────────────────────────────────────────────────────────────────


-- ── Step 0: drop indexes that block the MODIFY ────────────────────
-- ORA-01418 = "specified index does not exist" → idempotent no-op.
BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX idx_collection_building_month';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1418 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX idx_expense_building_month';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1418 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX idx_flat_dues_building';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1418 THEN RAISE; END IF;
END;
/


-- ── Step 1: widen flat_id / building_id to VARCHAR2(64) ───────────
-- Already-wide columns no-op silently because Oracle accepts
-- MODIFY to the same/larger size without complaint (Oracle does NOT
-- emit an error code for a MODIFY that's already a no-op — it just
-- returns). The EXCEPTION blocks catch the legacy
-- "data exists" / "already nullable" codes anyway, defensively.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection MODIFY (flat_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flat_maintenance_dues MODIFY (flat_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flat_maintenance_dues MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE building_society_config MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_expense MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/


-- ── Step 2: add the category column ───────────────────────────────
-- VARCHAR2(32) holds the MaintenanceCategory enum literal (longest
-- value is COMMON_AREA_SHARE at 17 chars). Nullable for backward
-- compat — pre-V4 rows have category=NULL, which the UI renders
-- as OTHER.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection ADD (category VARCHAR2(32))';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-01430 = "column being added already exists in table"
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/


-- ── Step 3: recreate the dropped indexes with identical defs ──────
-- ORA-00955 = "name is already used by an existing object"
-- ORA-01408 = "such column list already indexed"
-- Both = idempotent no-op.
BEGIN
    EXECUTE IMMEDIATE
        'CREATE INDEX idx_collection_building_month '
     || 'ON maintenance_collection (building_id, for_month DESC, status)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'CREATE INDEX idx_expense_building_month '
     || 'ON maintenance_expense (building_id, expense_month DESC, paid_on_date DESC)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'CREATE INDEX idx_flat_dues_building '
     || 'ON flat_maintenance_dues (building_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
