-- ─────────────────────────────────────────────────────────────────
--  V16 — Email verification at signup.
--
--  Adds two pieces:
--   1. user_details_table.email_verified — boolean column (1/0)
--      gating login when the global toggle is ON.
--   2. email_verification_tokens — single-use, time-bounded magic
--      link tokens emailed to the user post-signup. Mirrors the
--      password_reset_tokens shape so the verification flow can
--      lean on the same idioms (one row per send, expires_at +
--      consumed_at clock, daily janitor sweeps expired rows).
--
--  Toggle (system_settings.email_verification_required) is seeded
--  to 'false' so this lands dormant — no user-visible change until
--  the admin flips it ON from /admin/settings. The grandfather
--  UPDATE marks every pre-existing user as verified=1 so they
--  keep logging in normally even when the toggle is later flipped.
--
--  Idempotency. UPDATE only touches email_verified=0 rows, and
--  the system_settings INSERT is wrapped in a NOT EXISTS guard
--  in the parallel SchemaMigrationRunner (Flyway-disabled bootstrap
--  path) so re-runs are no-ops.
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE user_details_table
    ADD email_verified NUMBER(1) DEFAULT 0 NOT NULL;

CREATE TABLE email_verification_tokens (
    id            VARCHAR2(36) PRIMARY KEY,
    token         VARCHAR2(64) NOT NULL,
    user_id       NUMBER NOT NULL,
    expires_at    TIMESTAMP NOT NULL,
    consumed_at   TIMESTAMP,
    created_at    TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_email_verif_user
        FOREIGN KEY (user_id)
        REFERENCES user_details_table(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_email_verif_token
    ON email_verification_tokens (token);

-- Janitor index: the daily cleanup query (DELETE … WHERE expires_at < :cutoff)
-- becomes a ranged scan instead of a full-table sweep.
CREATE INDEX idx_email_verif_expires
    ON email_verification_tokens (expires_at);

-- Grandfather every existing user: they keep logging in normally
-- even after the admin flips the toggle ON later. Only new signups
-- post-V16 will start with email_verified=0.
UPDATE user_details_table
   SET email_verified = 1
 WHERE email_verified = 0;

-- Seed the toggle off by default. Admin flips it ON from
-- /admin/settings when ready to enforce verification on new signups.
INSERT INTO system_settings (setting_key, value)
VALUES ('email_verification_required', 'false');

COMMIT;
