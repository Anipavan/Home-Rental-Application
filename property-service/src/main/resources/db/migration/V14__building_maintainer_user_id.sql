-- ─────────────────────────────────────────────────────────────────
--  V14 — Two-facet building model.
--
--  Adds registered_buildings.maintainer_user_id: the auth-user who
--  registered THIS building for society management. Nullable — legacy
--  owner-created rental buildings have no maintainer facet and stay
--  NULL. New "I'm a maintainer" signups populate it.
--
--  This is a TRACKING column. The actual approval-routing identity
--  (who can approve maintainee join requests) still comes from
--  society_config.maintainer_user_id — that table already exists and
--  is the durable source of truth for "who runs this society". Having
--  the same value on the building row gives us a fast dedup lookup
--  ("does maintainer X's Sunshine Valley already exist as a society-
--  facet building?") without joining SocietyConfig on every search.
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE registered_buildings
    ADD maintainer_user_id VARCHAR2(64);

CREATE INDEX idx_buildings_maintainer
    ON registered_buildings (maintainer_user_id);

COMMIT;
