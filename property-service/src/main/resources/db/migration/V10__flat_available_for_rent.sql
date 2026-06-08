-- ─────────────────────────────────────────────────────────────────
--  V10 — Per-flat "listed for rent" toggle + flat_owner_id backfill.
--
--  Two related fixes:
--
--  1. AVAILABLE_FOR_RENT column on flats. The public browse page
--     was showing every flat where tenant_id IS NULL (i.e. every
--     vacant unit), which is wrong — owners want explicit control
--     over which of their flats are listed publicly. Owner-occupied
--     flats, flats under renovation, flats they only intend to
--     sell, etc. should NOT appear on the public browse until the
--     owner explicitly toggles them on. Default FALSE preserves the
--     intent for newly-created flats too.
--
--     We use Oracle 23's native BOOLEAN type (not the legacy
--     NUMBER(1) "boolean" convention) — Hibernate 6 + Oracle 23
--     enforces strict schema validation, and any new Java Boolean
--     field mapped to NUMBER(1) fails to start (see V9 for the
--     fix on requires_dual_approval).
--
--  2. FLAT_OWNER_ID backfill for flats added AFTER V8 ran. V8
--     backfilled existing flats at migration time, but new flats
--     created via the FlatService got NULL flat_owner_id because
--     the service layer didn't auto-set it. This backfill catches
--     up any orphan rows by inheriting the building's owner_id —
--     the same default V8 used.
-- ─────────────────────────────────────────────────────────────────

-- Step 1: add available_for_rent column. Default FALSE so existing
-- flats DON'T suddenly appear in the public browse when this ships.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flats ADD (available_for_rent BOOLEAN DEFAULT FALSE NOT NULL)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

-- Step 2: backfill flat_owner_id from building.owner_id for any
-- flat created after V8 (i.e. any row still NULL). Same source-of-
-- truth as V8's initial backfill.
UPDATE flats f
   SET f.flat_owner_id = (
        SELECT b.owner_id
          FROM registered_buildings b
         WHERE b.building_id = f.building_id)
 WHERE f.flat_owner_id IS NULL;

COMMIT;
