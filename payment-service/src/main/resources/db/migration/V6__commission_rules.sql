-- V6: commission_rules
--
-- Backs the admin-configurable platform-commission engine that runs at
-- payment-initiate time. One row per rule: the row with owner_id = NULL
-- is the global default; rows with a populated owner_id are per-owner
-- overrides that take precedence for that owner.
--
-- Rates are stored in basis points (1 bps = 0.01 %) so admin-entered
-- percentages persist without floating-point drift. A ₹10,000 rent at
-- 200 bps yields a ₹200 platform fee and ₹9,800 to the owner.

CREATE TABLE commission_rules (
    id                      VARCHAR2(36)   PRIMARY KEY,

    /* NULL = the single global default. Non-null = a per-owner override.
       Uniqueness (one global row, one row per owner) is enforced at the
       service layer via upsert semantics — Oracle unique indexes treat
       NULLs as distinct so we can't lean on the column constraint for
       the global-row invariant. */
    owner_id                VARCHAR2(64),

    /* Percentage in basis points. 0 = free, 200 = 2.00 %, 250 = 2.50 %.
       Capped at 10000 (100 %) to prevent an admin fat-finger from
       turning off owner payouts entirely. */
    rate_bps                NUMBER(6)      NOT NULL,

    /* Audit — who last edited this rule. Populated from
       Authentication.getName() at the controller layer. Nullable
       because the initial global row is seeded by V6 itself with no
       admin actor. */
    updated_by_admin_id     VARCHAR2(64),

    /* Free-text audit hint the admin can attach when creating an
       override — "loyalty discount", "premium listing", "waived for
       Q4 rollout", etc. Surfaced in the admin dashboard so future you
       remembers why. */
    notes                   VARCHAR2(500),

    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP      NOT NULL,

    /* Cap: sanity guard against a 12000-bps typo taking every rupee
       from the owner. Enforced at the DB level so no code path can
       bypass it. */
    CONSTRAINT chk_commission_rate_bps CHECK (rate_bps BETWEEN 0 AND 10000)
);

CREATE INDEX idx_commission_rules_owner ON commission_rules (owner_id);

-- Seed the global default at 0 bps so brand-new deployments start with
-- 100 % of the rent going to the owner. The admin flips this to 200 or
-- similar from /admin/commission once real transactions begin.
INSERT INTO commission_rules (id, owner_id, rate_bps, notes, created_at, updated_at)
VALUES (
    LOWER(SYS_GUID()),
    NULL,
    0,
    'Seeded default — 0 % commission. Change from /admin/commission when ready.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
