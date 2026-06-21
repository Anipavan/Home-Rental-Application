-- ─────────────────────────────────────────────────────────────────
--  V14 — Global system-toggle table.
--
--  Background. Until now there's been no way to flip a platform-level
--  feature on or off without a code deploy. Today's driver: the
--  maintainer activation fee. We want an admin to flip the gate on
--  or off from the admin Settings UI, without touching the backend.
--
--  Shape: key/value with audit. One row per toggle. New toggles in
--  the future land as additional rows — no schema churn each time
--  we add a feature flag.
--
--  Default value of the first row is 'false': on first deploy the
--  gate is OFF, so every new maintainer signup is free until the
--  admin turns it on. Matches the explicit decision recorded in the
--  approved plan.
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE system_settings (
    setting_key  VARCHAR2(60) PRIMARY KEY,
    value        VARCHAR2(255) NOT NULL,
    updated_at   TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    -- auth user id of the admin who flipped it last; null for the
    -- seeded default row that no human ever touched.
    updated_by   NUMBER
);

INSERT INTO system_settings (setting_key, value)
VALUES ('maintainer_payment_enabled', 'false');

COMMIT;
