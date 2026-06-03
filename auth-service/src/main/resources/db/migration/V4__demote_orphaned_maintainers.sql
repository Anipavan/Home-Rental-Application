-- ─────────────────────────────────────────────────────────────────
--  V4 — Revert orphaned MAINTAINER user_role values back to TENANT.
--
--  Background. Before V3 landed (today), the promote-tenant-to-
--  maintainer flow OVERWROTE three columns on the target row:
--    • user_password   ← the new temp password set by the owner
--    • user_role       ← TENANT → MAINTAINER
--    • tokens_revoked_before ← bumped to now()
--
--  Those overwrites destroyed the user's original tenant credential
--  AND their tenant identity (role flipped). V3 fixed this going
--  forward by storing the new credential in a SEPARATE column
--  (maintainer_password) and leaving user_role / user_password
--  intact.
--
--  But existing rows from the old broken flow are still stuck
--  with user_role=MAINTAINER. The login dual-credential path can
--  never fire for them because:
--    1. Their user_password matches the temp password they were
--       given by the owner — Spring's primary auth succeeds.
--    2. We never enter the maintainer_password fallback.
--    3. JWT carries role=MAINTAINER (from user_role).
--    4. They land on /maintainer with no way to reach /app.
--
--  This migration sweeps those rows back to user_role=TENANT.
--  Their user_password stays untouched (= whatever temp the owner
--  set), so they can still log in. After this migration the OWNER
--  can re-run promote-tenant on them — the new promote code only
--  writes maintainer_password, leaving user_password alone — and
--  THAT'S when they'll have a working two-password setup:
--    user_password         → tenant mode
--    maintainer_password   → maintainer mode
--
--  Why this is safe: MAINTAINER as a Roles enum value was
--  introduced specifically for the society feature shipped this
--  week. No user reaches MAINTAINER through any legitimate path
--  EXCEPT the (now-fixed) promote flow. So a blanket demotion
--  doesn't risk breaking a legitimate maintainer-only account.
--
--  Idempotent: re-running just becomes a no-op once no rows match.
-- ─────────────────────────────────────────────────────────────────

UPDATE user_details_table
   SET user_role = 'TENANT',
       record_updated_date = SYSTIMESTAMP
 WHERE user_role = 'MAINTAINER';

COMMIT;
