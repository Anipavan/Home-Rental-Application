-- ─────────────────────────────────────────────────────────────────
--  V16 — Society bank-config health flag.
--
--  Adds two columns to building_society_config so tenants who can't
--  complete a direct-UPI payment (invalid VPA, wrong handle, etc.)
--  can flag the config for the maintainer to fix. Regex validation
--  on the setup form catches typos in FORMAT; this catches typos in
--  the specific username part that only surface at UPI-resolution
--  time. No paid VPA-verification API involved.
--
--  bank_config_flagged_at  — non-null timestamp = flagged (dashboard
--                            renders a warning banner). Null =
--                            healthy.
--  bank_config_flag_reports — counter of tenant reports since the
--                             last clear. Reset to 0 when the
--                             maintainer edits upi_id / payee_name
--                             (auto-clear on fresh save).
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE building_society_config
    ADD (
        bank_config_flagged_at   TIMESTAMP,
        bank_config_flag_reports NUMBER(10) DEFAULT 0
    );

COMMIT;
