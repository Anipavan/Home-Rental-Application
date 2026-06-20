-- Add water-meter reading columns to the per-flat charges table.
--
-- Maintainer dashboard moves the "Common-area share" column out of
-- the matrix view and adds two new columns: "Previous Usage" and
-- "Current Usage". When the category is WATER_BILL, the maintainer
-- records these meter readings alongside the bill amount so every
-- stakeholder (resident, owner, anyone on the public ledger) can
-- independently verify the unit count behind the rupee figure.
--
-- Stored as NUMBER(12,2) — same precision as amount_due. Units (kL /
-- m³ / whatever the building's meter reports) are display-only; we
-- don't model a unit column because every flat in a building reads
-- from the same meter type so it's a constant per-building, not
-- per-row data. Add later if a building switches mid-tenure.
--
-- Both columns are nullable: legacy WATER_BILL rows pre-V13 have no
-- readings recorded, and the maintainer can still file a WATER_BILL
-- entry without readings (e.g. fixed share, no meter, lump-sum). UI
-- branches on null to show / hide the readings cell content.
--
-- Non-WATER_BILL categories simply leave both columns null — the UI
-- only surfaces them in the WATER_BILL cell. Storing them on every
-- row keeps the schema simple; the cost is a few null bytes per
-- non-water row which is negligible.
ALTER TABLE maintenance_collection
    ADD prev_usage_reading NUMBER(12, 2);

ALTER TABLE maintenance_collection
    ADD curr_usage_reading NUMBER(12, 2);
