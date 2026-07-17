-- ─────────────────────────────────────────────────────────────────
--  V15 — Flat society membership.
--
--  Splits two concepts that were previously conflated on the
--  {@code flats.tenant_id} column:
--
--    1. Rental tenant  — a person renting the flat from its owner.
--                        There is a lease. The owner tracks rent
--                        against this person. Stored on {@code
--                        flats.tenant_id} (unchanged).
--
--    2. Society member — a person who lives in the flat and is
--                        billed for maintenance/common-area dues.
--                        May or may not be paying rent. May in fact
--                        be the flat's own legal owner (owner-
--                        occupier). Stored here.
--
--  Before V15, a self-registered "I'm a maintainee" signup set
--  {@code flats.tenant_id} to the maintainee's authUserId, so the
--  building owner's Flats page rendered them as a rental tenant.
--  After V15, {@code applyResidentApproval} writes into this table
--  and leaves {@code tenant_id} alone.
--
--  Composite PK (flat_id, user_id): a user can appear at most once
--  per flat. Re-registration is idempotent (the service layer
--  reactivates by flipping {@code is_active} back to 1).
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE flat_society_membership (
    flat_id       VARCHAR2(64) NOT NULL,
    user_id       VARCHAR2(64) NOT NULL,
    joined_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    approved_by   VARCHAR2(64),
    is_active     NUMBER(1)   DEFAULT 1 NOT NULL,
    CONSTRAINT pk_flat_society_membership PRIMARY KEY (flat_id, user_id)
);

-- "All flats this user is a society member of" — used to render the
-- maintainee's own Society page.
CREATE INDEX idx_fsm_user ON flat_society_membership (user_id);

-- "Every active society member of this flat" — used by the
-- maintainer's per-flat dashboard and by maintenance billing.
CREATE INDEX idx_fsm_flat_active
    ON flat_society_membership (flat_id, is_active);

COMMIT;
