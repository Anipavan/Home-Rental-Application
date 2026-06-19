-- Tags every Payment row with what it's for, so the tenant Payments page
-- can split Rent vs Maintenance into separate tabs and the SuccessView
-- can route the user to the right tab on return from Razorpay.
--
-- Values currently in use:
--   RENT            - monthly rent invoice (created by PaymentSchedulerJob
--                     or manual admin POST /payments).
--   SOCIETY_CHARGE  - resident-initiated bulk-pay or per-charge society
--                     payment (created by POST /payments/society-charge,
--                     called by property-service via Feign).
--
-- Default 'RENT' on the column itself so every existing row backfills as
-- rent (which is what they all are pre-society-bridge) and any future
-- code path that forgets to set sourceType still gets a sensible value
-- instead of a NULL the UI would have to hide.
ALTER TABLE payments
    ADD source_type VARCHAR2(30) DEFAULT 'RENT' NOT NULL;

-- Backfill existing rows for safety in case Oracle didn't materialise
-- the DEFAULT for already-existing rows (varies by Oracle version).
UPDATE payments SET source_type = 'RENT' WHERE source_type IS NULL;

-- Index so the upcoming "history filtered by sourceType" query stays
-- fast even after thousands of payments. Composite on (tenant, source)
-- because the lookup pattern is always "this tenant's RENT history" or
-- "this tenant's SOCIETY_CHARGE history".
CREATE INDEX idx_payments_tenant_source ON payments (tenant_id, source_type);
