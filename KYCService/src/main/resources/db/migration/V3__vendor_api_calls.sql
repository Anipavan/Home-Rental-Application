-- ─────────────────────────────────────────────────────────────────
--  V3 — Create vendor_api_calls audit table.
--
--  One row per outbound call to any third-party vendor (Sandbox NSDL,
--  Sandbox OCR, Razorpay, Resend, etc.). Drives the admin "Vendor
--  usage" dashboard and powers billing-alert escalations.
--
--  Lives in kyc-service today because that's where the first billing
--  issue surfaced; schema is intentionally vendor-agnostic so other
--  services (payment-service for Razorpay, notification-service for
--  Resend) can populate the same table when we consolidate.
--
--  Each statement is wrapped in a PL/SQL block that catches the
--  "name already used" exception (ORA-00955 for tables/constraints,
--  ORA-01408 for column-already-indexed). This keeps the migration
--  idempotent on prod, which had the table + indexes hand-created
--  during the initial rollout (kyc-service couldn't boot until the
--  table existed; this migration was added retroactively). Fresh DBs
--  see a clean create.
--
--  Schema-validation in prod (ddl-auto=validate) reads this table
--  shape against the VendorApiCall entity — keep them in lockstep.
-- ─────────────────────────────────────────────────────────────────

-- Table
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE vendor_api_calls (
            id                    VARCHAR2(36)  NOT NULL,
            vendor_name           VARCHAR2(64)  NOT NULL,
            vendor_endpoint       VARCHAR2(256),
            status                VARCHAR2(32)  NOT NULL,
            error_code            VARCHAR2(32),
            error_message         VARCHAR2(1024),
            occurred_at           TIMESTAMP     NOT NULL,
            response_time_ms      NUMBER(10),
            triggered_by_user_id  VARCHAR2(64),
            CONSTRAINT pk_vendor_api_calls PRIMARY KEY (id)
        )';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-00955: name is already used by an existing object.
        -- Anything else, re-raise so the migration fails loudly.
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- Both indexes back the admin dashboard's two query shapes:
--   1. "Show me the last 10 calls for vendor X" → idx_vendor_occurred
--   2. "Aggregate counts per (vendor, status) for the last 30 days"
--      → idx_vendor_status
-- Listing occurred_at DESC matches Oracle's index scan direction so
-- ORDER BY occurred_at DESC doesn't require a separate sort step.

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_vendor_occurred
        ON vendor_api_calls (vendor_name, occurred_at DESC)';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-00955: name already used. ORA-01408: column list already
        -- indexed (would fire if a same-shape index exists under a
        -- different name).
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_vendor_status
        ON vendor_api_calls (vendor_name, status, occurred_at DESC)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
