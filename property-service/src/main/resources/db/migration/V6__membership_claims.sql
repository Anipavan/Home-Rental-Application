-- ─────────────────────────────────────────────────────────────────
--  V6 — Self-service membership claims.
--
--  Owners no longer have to manually pick a tenant and promote them
--  to maintainer. Instead, anyone can register on the signup page
--  and submit a claim against a specific building:
--
--    * requested_role = MAINTAINER  — "I want to run this society"
--    * requested_role = RESIDENT    — "I live in flat 201 of this
--                                     building, please attach me to it"
--
--  The claim sits in status=PENDING until the building's owner
--  approves or rejects it from their dashboard. Approve flows:
--
--    MAINTAINER  → property-service updates society_config.maintainer_
--                  user_id (REPLACING the previous maintainer if any —
--                  the owner is intentionally green-lighting the swap)
--                  AND calls auth-service to bump the user's role to
--                  MAINTAINER.
--    RESIDENT    → property-service binds the user as the tenant of
--                  the claimed flat (same write path as the existing
--                  owner-side flat-assignment flow). Role stays TENANT.
--
--  Indexed on (building_id, status) to support the owner-side
--  "show me my pending requests" query, and on (user_id, status)
--  for the user-side "show me my own pending claims" query.
--
--  Wrapped in idempotent EXCEPTION blocks so the migration is safe
--  to re-run on a partially-applied state.
-- ─────────────────────────────────────────────────────────────────


-- ── 1. membership_claims ──────────────────────────────────────────
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE membership_claims (
            id                       VARCHAR2(36)  NOT NULL,
            building_id              VARCHAR2(36)  NOT NULL,
            -- authUserId of the claimant. We DO NOT FK to a users
            -- table because users live in auth-service / user-service;
            -- the cross-service FK would couple migrations.
            user_id                  VARCHAR2(64)  NOT NULL,
            -- MAINTAINER | RESIDENT — controls what approval grants.
            requested_role           VARCHAR2(16)  NOT NULL,
            -- Flat number the user claims to live in. Required for
            -- RESIDENT claims (used to look up the flat at approval
            -- time); optional for MAINTAINER claims (we record what
            -- they typed but do not bind a flat).
            claimed_flat_number      VARCHAR2(32),
            -- PENDING | APPROVED | REJECTED | WITHDRAWN. WITHDRAWN
            -- is for the claimant cancelling their own pending claim.
            status                   VARCHAR2(16)  DEFAULT ''PENDING'' NOT NULL,
            -- Optional free-text the claimant adds when submitting
            -- ("I am tenant of flat 201 since June 2024") — gives
            -- the owner context for the approve/reject decision.
            applicant_note           VARCHAR2(500),
            -- Owner''s decision note (optional). Shown to the
            -- claimant when their claim resolves.
            decision_note            VARCHAR2(500),
            decided_by_user_id       VARCHAR2(64),
            created_at               TIMESTAMP     NOT NULL,
            decided_at               TIMESTAMP,
            CONSTRAINT pk_membership_claims PRIMARY KEY (id),
            CONSTRAINT chk_claims_role  CHECK (requested_role IN (''MAINTAINER'', ''RESIDENT'')),
            CONSTRAINT chk_claims_status CHECK (status IN (''PENDING'', ''APPROVED'', ''REJECTED'', ''WITHDRAWN''))
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/


-- ── 2. Indexes ────────────────────────────────────────────────────
-- (building_id, status) — owner pending-requests widget. Most queries
-- filter status=PENDING and group by building; the leading building_id
-- makes the per-building owner dashboard view a clean index scan.
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_claims_building_status
        ON membership_claims (building_id, status)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

-- (user_id, status) — claimant''s "my pending claims" view, also used
-- by the dedup check ("user already has a PENDING claim for building X").
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_claims_user_status
        ON membership_claims (user_id, status)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
