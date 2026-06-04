-- ─────────────────────────────────────────────────────────────────
--  V5 — Multi-line society charges + collection bank/UPI fields.
--
--  Two related changes that together let a maintainer record more
--  than one charge per flat per month (water bill + maintenance +
--  electricity all as separate rows) AND give the tenant a Pay
--  button per row that generates a UPI QR code targeting the
--  society's common collection account.
--
--  1. CHARGE GRANULARITY
--     V3 declared UNIQUE (flat_id, for_month) on maintenance_collection,
--     which capped each flat at exactly one charge per month. Now we
--     drop that constraint and replace it with UNIQUE
--     (flat_id, for_month, category) — a flat can have one row per
--     category, but never two of the same category. So:
--       (FLAT-A, 2026-06, WATER_BILL)        -- ok
--       (FLAT-A, 2026-06, MAINTENANCE)       -- ok, different category
--       (FLAT-A, 2026-06, WATER_BILL) again  -- rejected, duplicate
--
--     Before the swap we backfill any pre-V4 rows that have NULL
--     category by setting them to MAINTENANCE — Oracle treats
--     NULL keys as distinct, so leaving them NULL would technically
--     work, but the application reads category=null as OTHER in
--     the UI, which is confusing. MAINTENANCE is the right default
--     because those rows were the "single per-flat default charge"
--     under the old model.
--
--  2. COLLECTION BANK / UPI FIELDS on building_society_config
--     The maintainer (or owner) plugs in the society's common bank
--     account once; the tenant Pay flow then renders a client-side
--     QR with upi://pay?pa=<upi_id>&pn=<payee>&am=<amount>&cu=INR
--     so residents can scan + pay from any UPI app. account_number
--     + ifsc_code are informational — the maintainer can include
--     them in a printed notice for tenants who can't or won't pay
--     via UPI. They're nullable: if a society only wants UPI, they
--     can leave the bank-account fields blank.
--
--  Wrapped in idempotent EXCEPTION blocks so the migration is safe
--  to re-run on partially-applied state.
-- ─────────────────────────────────────────────────────────────────


-- ── Step 1: backfill NULL categories so they don't break the
--           new composite unique constraint below ──
UPDATE maintenance_collection
   SET category = 'MAINTENANCE'
 WHERE category IS NULL;

COMMIT;


-- ── Step 2: drop the old single-row-per-month unique constraint ──
-- ORA-02443 'cannot drop constraint - nonexistent constraint' is fine
-- (re-runs hit this branch).
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection DROP CONSTRAINT uq_collection_flat_month';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2443 THEN RAISE; END IF;
END;
/


-- ── Step 3: add the new (flat_id, for_month, category) unique ──
-- ORA-00955 'name is already used' is fine — re-run no-op.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE maintenance_collection '
     || 'ADD CONSTRAINT uq_collection_flat_month_category '
     || 'UNIQUE (flat_id, for_month, category)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/


-- ── Step 4: bank / UPI columns on building_society_config ──
-- All nullable. Existing societies get NULL → the Pay-Now button
-- on the tenant UI stays hidden until the maintainer fills in
-- at least the UPI ID. account_number / ifsc are pure informational
-- additions — frontend renders them as plain text below the QR.

-- upi_id (e.g. anirudhresi@oksbi). Practical UPI handle max is
-- around 50 chars (provider + 30-char ID part); 64 is comfortable
-- headroom.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE building_society_config ADD (upi_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

-- payee_name — how the UPI app displays the recipient. The society
-- display name is usually a good default; we keep this separate so
-- the QR can be addressed to the registered bank-account holder
-- name (e.g. "Anirudh Welfare Society Pvt Ltd") which often differs
-- from the friendly society display name.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE building_society_config ADD (payee_name VARCHAR2(200))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

-- account_number — informational, displayed as a fallback under
-- the QR (some tenants prefer to add the account as a beneficiary
-- in their banking app instead of scanning). 32 chars covers every
-- Indian bank account format with comfortable headroom.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE building_society_config ADD (account_number VARCHAR2(32))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

-- ifsc_code — IFSC is exactly 11 chars (4 alpha + 0 + 6 alnum).
-- 16 chars handles the spec plus any future format extension.
BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE building_society_config ADD (ifsc_code VARCHAR2(16))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/
