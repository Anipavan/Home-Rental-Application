-- ─────────────────────────────────────────────────────────────────
--  V4 — Two related fixes for the per-flat maintenance flow.
--
--  1. Widen flat_id columns on the V3 society tables from
--     VARCHAR2(36) to VARCHAR2(64).
--
--     Why: actual flat IDs on this deploy are FLT-<uuid> (e.g.
--     FLT-5911ea4e-0648-4670-9d3f-33a0d44a5745), which is 40
--     characters. The V3 migration optimistically assumed bare
--     UUIDs (36 chars) and Oracle rejects the INSERT with
--     ORA-12899: value too large for column. The maintainer's
--     "Set amount" save was 500-ing because of this.
--
--     building_id columns also widened to 64 for symmetry —
--     today's building IDs are bare UUIDs, but it's cheap
--     insurance against a future BLD-<uuid> rename.
--
--  2. Add a `category` column to maintenance_collection.
--
--     Why: the maintainer needs to tag each per-flat charge as
--     Water bill / Maintenance / Gas / Electricity / Common-area
--     share / Other so tenants can see WHY their bill jumped
--     this month, not just "₹2,500 — pay it". The dashboard's
--     "Set amount" dialog gains a dropdown above the amount
--     field for this. Existing rows have category = NULL which
--     the UI renders as 'OTHER'.
--
--  All ALTER statements are wrapped in EXCEPTION blocks so the
--  migration is idempotent — re-running against a partially-
--  migrated schema is a no-op rather than a hard fail.
-- ─────────────────────────────────────────────────────────────────


-- ── 1a. maintenance_collection.flat_id 36 → 64 ────────────────────
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection MODIFY (flat_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-01441 'column to be modified must be empty to decrease
        -- precision/scale' won't fire (we're growing). ORA-01451
        -- (column already nullable) won't fire either. We catch
        -- anything else only to log + re-raise.
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

-- ── 1b. maintenance_collection.building_id 36 → 64 ────────────────
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

-- ── 1c. flat_maintenance_dues.flat_id 36 → 64 ─────────────────────
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flat_maintenance_dues MODIFY (flat_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

-- ── 1d. flat_maintenance_dues.building_id 36 → 64 ─────────────────
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flat_maintenance_dues MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

-- ── 1e. building_society_config.building_id 36 → 64 ───────────────
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE building_society_config MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/

-- ── 1f. maintenance_expense.building_id 36 → 64 ───────────────────
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_expense MODIFY (building_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1442, -1451, -1448) THEN RAISE; END IF;
END;
/


-- ── 2. maintenance_collection.category ───────────────────────────
-- Free-string ENUM in app code (MaintenanceCategory.java) — keeping
-- it as VARCHAR2 means adding a new value is a one-line enum change,
-- no DDL required.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection ADD (category VARCHAR2(32))';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-01430 'column being added already exists in table' is fine.
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/
