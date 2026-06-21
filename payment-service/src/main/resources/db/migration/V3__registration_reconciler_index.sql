-- ─────────────────────────────────────────────────────────────────
--  V3 — Composite index for the RegistrationActivationReconciler.
--
--  The scheduler runs every 5 minutes and asks:
--    SELECT * FROM payments
--     WHERE source_type='MAINTAINER_REGISTRATION'
--       AND status='PAID'
--       AND payment_date > :since
--
--  Without an index the planner falls back to idx_payments_status
--  (single-column on status) and post-filters on source_type +
--  payment_date — fine at today's volume but a hot spot once PAID
--  rent payments accumulate. A composite index over the exact three
--  predicates lets Oracle answer the query with an index range scan
--  on a tiny subset of rows.
--
--  Column order matters: equality predicates first
--  (source_type, status), then the range predicate (payment_date)
--  last so the index can both seek to the matching rows and order
--  them by date for free.
-- ─────────────────────────────────────────────────────────────────

CREATE INDEX idx_payments_reg_reconciler
    ON payments (source_type, status, payment_date);
