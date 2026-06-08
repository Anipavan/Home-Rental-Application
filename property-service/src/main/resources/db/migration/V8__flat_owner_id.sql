-- ─────────────────────────────────────────────────────────────────
--  V8 — Per-flat ownership.
--
--  Until V7 the data model assumed a single owner per BUILDING was
--  the de-facto landlord for every flat. That fits a rental-property
--  building (one owner rents out every unit), but breaks the more
--  common Indian case where flats are individually sold and each
--  one has its own owner. Same physical building, many owners.
--
--  This migration adds {@code flat.flat_owner_id} as the source of
--  truth for who owns each flat. Behaviour:
--
--    flat_owner_id == building.owner_id            (legacy / default)
--      The building owner also owns this flat. No change in behaviour
--      vs the pre-V8 world — rent goes to the building owner, lease
--      lists them as Party A, society dues are theirs.
--
--    flat_owner_id != building.owner_id            (split ownership)
--      Someone else owns this flat. Rent goes to THEM, lease lists
--      THEM as Party A, society dues are billed to THEM. The building
--      owner only retains control over common areas / society config.
--
--    flat_owner_id == tenant_id                    (owner-occupier)
--      The flat-owner lives in their own flat. No landlord-tenant
--      relationship exists for this flat; dashboard hides the lease
--      section, payments are self-billed (society dues only).
--
--  Backfill: every existing flat gets flat_owner_id =
--  registered_buildings.owner_id, so the V8 deploy is a no-op from
--  the user's perspective — behaviour shifts only once an operator
--  explicitly reassigns ownership of a specific flat (via the new
--  FLAT_OWNER membership-claim flow, or directly from a building-owner
--  reassign UI we'll add later).
--
--  The column is NOT marked NOT NULL yet — defaulting to NULL would
--  break the (flat_owner_id == NULL means "use building owner") read
--  path in some places. We rely on the UPDATE backfill at the end
--  of this migration to populate every existing row, and the entity
--  layer enforces non-null on writes going forward.
-- ─────────────────────────────────────────────────────────────────

-- Step 1: add the column (nullable initially so the ADD itself never
-- fails on rows that don't yet have a value).
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flats ADD (flat_owner_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

-- Step 2: backfill from the parent building's owner_id. Rows that
-- already have flat_owner_id set (re-running this migration) are
-- left alone via the IS NULL guard.
UPDATE flats f
   SET f.flat_owner_id = (
        SELECT b.owner_id
          FROM registered_buildings b
         WHERE b.building_id = f.building_id)
 WHERE f.flat_owner_id IS NULL;

COMMIT;

-- Step 3: index on flat_owner_id for the "list every flat I own"
-- query that powers the per-flat-owner dashboard.
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_flat_owner ON flats (flat_owner_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

-- Step 4: widen the V6 membership_claims requested_role CHECK
-- constraint to include FLAT_OWNER (V8). The old CHECK only allowed
-- MAINTAINER / RESIDENT; trying to insert a FLAT_OWNER row without
-- this step would trip ORA-02290 'check constraint violated'.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims DROP CONSTRAINT chk_claims_role';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2443 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims '
     || 'ADD CONSTRAINT chk_claims_role '
     || 'CHECK (requested_role IN (''MAINTAINER'', ''RESIDENT'', ''FLAT_OWNER''))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/
