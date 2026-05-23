-- ─────────────────────────────────────────────────────────────────
--  V2 — Add scheduled_vacate_comments column to flats.
--
--  Captures the tenant's free-text reason for vacating ("relocating
--  for work", "moving to a bigger flat", "owner not responsive", etc.).
--  Surfaced to the owner on the 10-day warning email + flat-detail
--  screen so they can act on recurring issues.
--
--  Nullable — pre-existing scheduled vacates (legacy rows) keep the
--  column NULL. The application code coerces NULL to "Not provided"
--  in the UI.
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE flats ADD scheduled_vacate_comments VARCHAR2(1000);
