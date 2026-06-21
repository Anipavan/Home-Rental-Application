-- ─────────────────────────────────────────────────────────────────
--  V15 — Maintainer-payment trial + skip columns.
--
--  Background. The hard paywall shipped in V5 (disable_reason =
--  'REGISTRATION_PAYMENT_PENDING') is being replaced with a softer,
--  in-app gate: 30-day free trial → two staggered skips → forced
--  payment. The state machine reads from these four columns:
--
--    payment_trial_started_at  — when the trial clock started
--                                (== signup time for new users,
--                                == record_created_date for grandfathered).
--    payment_skip_count        — 0, 1, or 2. The third prompt is mandatory.
--    payment_last_skip_at      — used to compute the 4-day grace window
--                                after each skip.
--    payment_paid_at           — set the moment the maintainer either
--                                pays (modal-driven Razorpay flow) or
--                                gets grandfathered (toggle OFF at
--                                signup, or pre-existing row at the time
--                                this migration runs). Non-null = PAID.
--
--  Grandfathering rule. Every row already in user_details_table when
--  V15 lands gets payment_paid_at = record_created_date. That puts
--  them permanently in the PAID state regardless of any future toggle
--  flip — they signed up under the no-payment regime, they keep their
--  no-payment status forever. The hard-paywall behaviour from V5 is
--  effectively neutralised: any row currently disabled for
--  REGISTRATION_PAYMENT_PENDING also gets grandfathered here, since
--  we're rolling back that gate.
--
--  Idempotency. The UPDATE only touches rows where payment_paid_at
--  IS NULL — re-running the migration (Flyway won't, but the parallel
--  SchemaMigrationRunner does on every boot) is a no-op once every
--  row has a value.
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE user_details_table ADD payment_trial_started_at TIMESTAMP;
ALTER TABLE user_details_table ADD payment_skip_count NUMBER(2) DEFAULT 0 NOT NULL;
ALTER TABLE user_details_table ADD payment_last_skip_at TIMESTAMP;
ALTER TABLE user_details_table ADD payment_paid_at TIMESTAMP;

UPDATE user_details_table
   SET payment_paid_at         = record_created_date,
       payment_trial_started_at = record_created_date
 WHERE payment_paid_at IS NULL;

-- Reverse the V5 gate for any row currently sitting in
-- REGISTRATION_PAYMENT_PENDING: clear the reason + flip enabled back
-- on. The grandfather above already set payment_paid_at for them.
UPDATE user_details_table
   SET disable_reason = NULL,
       enabled        = 1
 WHERE disable_reason = 'REGISTRATION_PAYMENT_PENDING';

COMMIT;
