-- ─────────────────────────────────────────────────────────────────
--  V11 — Flip the default for flats.available_for_rent to TRUE.
--
--  V10 shipped this column with DEFAULT FALSE on the theory that
--  owners should opt-in to public listing. In practice that's the
--  wrong default — owners who add a flat almost always WANT it
--  surfaced (otherwise why register it?), and forcing them to
--  re-open the edit dialog to flip a toggle they never intentionally
--  set is friction.
--
--  Reverse the polarity:
--    1. Column default → TRUE   (new flats are listed by default).
--    2. Backfill all existing FALSE rows → TRUE so the change is
--       consistent across legacy data. Owners who want a flat
--       hidden (owner-occupied, under renovation, sale-only) still
--       flip the toggle off — same UI, different default.
--
--  Idempotency:
--    - The MODIFY guard tolerates re-runs on environments where the
--      default has already been changed (Oracle has no IF NOT EXISTS
--      for column defaults so we catch the no-op explicitly).
--    - The UPDATE is naturally idempotent (FALSE→TRUE; re-running
--      finds 0 rows to flip).
--
--  Oracle 23ai BOOLEAN: the literal TRUE / FALSE keywords work in
--  DDL DEFAULT clauses AND in DML SET clauses, matching V10.
-- ─────────────────────────────────────────────────────────────────

-- Step 1: change the column default to TRUE for any future INSERTs
-- that omit available_for_rent. Existing rows are unaffected by
-- this DDL — step 2 handles them.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE flats MODIFY (available_for_rent BOOLEAN DEFAULT TRUE NOT NULL)';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-01442/ORA-01451: column already has the requested
        -- default / nullability — treat as success.
        IF SQLCODE NOT IN (-1442, -1451) THEN RAISE; END IF;
END;
/

-- Step 2: backfill all currently-FALSE rows to TRUE so the new
-- default is reflected uniformly across legacy data. Owners who
-- genuinely want a flat hidden re-toggle it through the edit
-- dialog after deploy.
UPDATE flats
   SET available_for_rent = TRUE
 WHERE available_for_rent = FALSE;

COMMIT;
