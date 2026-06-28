-- ─────────────────────────────────────────────────────────────────
--  V17 — Multi-role identity foundation.
--
--  Adds a user_roles join table backing a Set<Roles> on UserDetails.
--  The existing user_details_table.user_role column stays in place
--  for one release as the "primary role" — it keeps every existing
--  consumer (gateway role headers, frontend role-routing, every
--  @PreAuthorize on a controller) working unchanged. Multi-role
--  capability lights up when later commits start populating extra
--  rows here.
--
--  Backfill rule: every existing user gets exactly one row inserted
--  pairing their (id, user_role). That makes the join table a true
--  superset on day one — any code that reads from userRoles sees at
--  minimum the same role the legacy column carries.
--
--  Composite PK (user_id, role) is intentional: it's both the de-dup
--  guard AND the lookup index. A user can hold each Role at most once.
--  ON DELETE CASCADE so deleting an auth row cleans up role rows
--  automatically — important because the user_details_table never has
--  a soft-delete; row removal is the only delete path.
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE user_roles (
    user_id  NUMBER NOT NULL,
    role     VARCHAR2(32) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES user_details_table(id)
        ON DELETE CASCADE
);

-- Index on role alone supports the "list users by role" query that
-- AuthServiceImpl.getUsersByRole today serves off the legacy column;
-- once we cut over, the same query will JOIN through this index.
CREATE INDEX idx_user_roles_role ON user_roles (role);

-- Backfill every existing user. WHERE NOT EXISTS makes the INSERT
-- idempotent so a re-run (or the parallel SchemaMigrationRunner
-- mirror) is a no-op.
INSERT INTO user_roles (user_id, role)
SELECT u.id, u.user_role
  FROM user_details_table u
 WHERE u.user_role IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM user_roles r
        WHERE r.user_id = u.id AND r.role = u.user_role);

COMMIT;
