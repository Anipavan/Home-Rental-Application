-- ─────────────────────────────────────────────────────────────────
--  V3 — Building society / common-area maintenance tables.
--
--  Per the rollout plan: each building has a one-time "society
--  config" (who manages it + what the default monthly due is +
--  the read-only shareable public-view token). The maintainer (who
--  can be the building owner themselves, or a nominated third
--  party) records monthly common expenses against the building —
--  water bill, electricity-common, security salary, gardener,
--  cleaner, lift AMC, etc. Tenants see a transparent ledger;
--  monthly per-flat shares are tracked as collections that the
--  maintainer manually marks paid until the payment-integration
--  follow-up wires Razorpay/UPI.
--
--  Money DOES NOT flow through this layer today. This is pure
--  bookkeeping — the entire payment plumbing (society bank account,
--  UPI QR generation, Razorpay reconciliation) lands in a later
--  milestone. The schema includes the keys those flows will need
--  (collection.amount_paid, paid_via, payment_id) so the migration
--  later is additive-only, not a rename / drop.
-- ─────────────────────────────────────────────────────────────────


-- ── 1. building_society_config ────────────────────────────────────
-- One row per building that has society / common-area billing
-- enabled. Created by the owner via /society/{buildingId}/setup.
-- The building_id is unique — at most one society config per
-- building. Owners who don't want this feature simply never create
-- a row; nothing in the rest of the app breaks.
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE building_society_config (
            id                              VARCHAR2(36)  NOT NULL,
            building_id                     VARCHAR2(36)  NOT NULL,
            -- DAY-OF-MONTH the dues are considered "due" by. Display
            -- only for now (no auto-overdue cron until payments wire).
            monthly_due_day                 NUMBER(2)     DEFAULT 5 NOT NULL,
            -- Per-flat default; overridable per-flat via flat_maintenance_dues.
            default_per_flat_amount         NUMBER(12,2)  NOT NULL,
            -- The maintainer is a real user (an existing OWNER who self-
            -- assigned, or a third-party MAINTAINER who accepted an
            -- invite). authUserId from auth-service.
            maintainer_user_id              VARCHAR2(64)  NOT NULL,
            -- Random URL-safe token. Tenants share it in their building
            -- WhatsApp group; anyone with the link gets the read-only
            -- ledger. Token rotates if it leaks (regenerate-token API).
            public_view_token               VARCHAR2(64)  NOT NULL,
            -- Display name for the society on the ledger header +
            -- public view title bar. Defaults to the building name
            -- but owners often want "<Building> Residents Welfare Fund".
            society_display_name            VARCHAR2(200),
            created_at                      TIMESTAMP     NOT NULL,
            updated_at                      TIMESTAMP     NOT NULL,
            CONSTRAINT pk_building_society_config PRIMARY KEY (id),
            CONSTRAINT uq_society_building UNIQUE (building_id),
            CONSTRAINT uq_society_token UNIQUE (public_view_token)
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_society_maintainer
        ON building_society_config (maintainer_user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/


-- ── 2. flat_maintenance_dues ──────────────────────────────────────
-- Per-flat override of the building's default_per_flat_amount.
-- Larger flats or units with extra amenities (corner flat, terrace
-- access) often pay more — this lets owners set per-flat amounts
-- without polluting the building config. Absent row = use the
-- building default.
--
-- effective_from_month lets a mid-year fee revision (e.g. society
-- AGM voted in a ₹500 hike from October) take effect on the right
-- month without rewriting the history of paid collections.
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE flat_maintenance_dues (
            id                       VARCHAR2(36)  NOT NULL,
            building_id              VARCHAR2(36)  NOT NULL,
            flat_id                  VARCHAR2(36)  NOT NULL,
            monthly_amount           NUMBER(12,2)  NOT NULL,
            effective_from_month     VARCHAR2(7)   NOT NULL,
            notes                    VARCHAR2(500),
            created_at               TIMESTAMP     NOT NULL,
            updated_at               TIMESTAMP     NOT NULL,
            CONSTRAINT pk_flat_maintenance_dues PRIMARY KEY (id),
            CONSTRAINT uq_flat_dues_effective UNIQUE (flat_id, effective_from_month)
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_flat_dues_building
        ON flat_maintenance_dues (building_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/


-- ── 3. maintenance_expense ────────────────────────────────────────
-- The actual ledger. Every common-area bill the maintainer pays
-- lands here. Linked to a building + a month (YYYY-MM) so monthly
-- views slice cleanly without recomputing dates client-side.
--
-- The category / subcategory split keeps the dashboard chart
-- readable ("UTILITY: ₹4,200" then drill down to water/electricity)
-- while not forcing an inflexible enum at the subcategory level —
-- the subcategory is a free-form-ish string so a maintainer can
-- write "Annual lift AMC top-up" without us shipping a 50-value
-- enum that we'd have to update every quarter.
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE maintenance_expense (
            id                       VARCHAR2(36)  NOT NULL,
            building_id              VARCHAR2(36)  NOT NULL,
            expense_month            VARCHAR2(7)   NOT NULL,
            category                 VARCHAR2(32)  NOT NULL,
            subcategory              VARCHAR2(100),
            amount                   NUMBER(12,2)  NOT NULL,
            vendor_name              VARCHAR2(200),
            paid_on_date             DATE          NOT NULL,
            receipt_doc_id           VARCHAR2(36),
            notes                    VARCHAR2(1000),
            added_by_user_id         VARCHAR2(64)  NOT NULL,
            added_at                 TIMESTAMP     NOT NULL,
            updated_at               TIMESTAMP     NOT NULL,
            CONSTRAINT pk_maintenance_expense PRIMARY KEY (id)
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_expense_building_month
        ON maintenance_expense (building_id, expense_month DESC, paid_on_date DESC)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/


-- ── 4. maintenance_collection ─────────────────────────────────────
-- One row per (flat, month). Generated lazily when the maintainer
-- opens a month for the first time, OR pre-generated by a future
-- cron when payment integration ships. Status flows:
--   DUE    — month opened, tenant hasn't paid yet (default)
--   PAID   — tenant paid; maintainer (manually for now, via
--            webhook later) confirmed
--   WAIVED — owner / maintainer chose to skip this month for this
--            flat (vacant, hardship, owner-occupied, etc.)
--
-- amount_paid + paid_via + payment_id are nullable today; the
-- payment-integration milestone fills them. Keeping them in schema
-- now means that follow-up is a NULL-set-to-VALUE update, not an
-- ALTER TABLE on a populated table.
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE maintenance_collection (
            id                       VARCHAR2(36)  NOT NULL,
            building_id              VARCHAR2(36)  NOT NULL,
            flat_id                  VARCHAR2(36)  NOT NULL,
            for_month                VARCHAR2(7)   NOT NULL,
            amount_due               NUMBER(12,2)  NOT NULL,
            amount_paid              NUMBER(12,2),
            paid_via                 VARCHAR2(32),
            payment_id               VARCHAR2(36),
            paid_on                  DATE,
            status                   VARCHAR2(20)  DEFAULT ''DUE'' NOT NULL,
            marked_by_user_id        VARCHAR2(64),
            notes                    VARCHAR2(500),
            created_at               TIMESTAMP     NOT NULL,
            updated_at               TIMESTAMP     NOT NULL,
            CONSTRAINT pk_maintenance_collection PRIMARY KEY (id),
            CONSTRAINT uq_collection_flat_month UNIQUE (flat_id, for_month)
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_collection_building_month
        ON maintenance_collection (building_id, for_month DESC, status)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
