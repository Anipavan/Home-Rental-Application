-- ─────────────────────────────────────────────────────────────────
--  V17 — Society announcements.
--
--  Building-scoped announcements the maintainer (or owner) posts for
--  residents — building notices, water-tank shutdowns, festival
--  timings, AGM reminders, etc. Residents (tenants of the building
--  + active society members) see them on their /app/society page.
--
--  Simple shape — title + body, timestamps, audit-lite. No pinning,
--  no attachments, no read-receipts for MVP; those can layer on
--  later if the pattern earns them.
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE society_announcements (
    id             VARCHAR2(36) PRIMARY KEY,
    building_id    VARCHAR2(64) NOT NULL,
    author_user_id VARCHAR2(64) NOT NULL,
    title          VARCHAR2(200) NOT NULL,
    body           VARCHAR2(4000) NOT NULL,
    created_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

-- "Latest N announcements for this building" — the driving query.
CREATE INDEX idx_sa_building_created
    ON society_announcements (building_id, created_at DESC);

COMMIT;
