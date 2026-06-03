-- ─────────────────────────────────────────────────────────────────
--  V3 — Add maintainer_password column to user_details_table.
--
--  Background: the original "promote tenant to maintainer" flow
--  overwrote user_password + user_role, which permanently lost the
--  tenant's original credentials. A user who was BOTH a tenant
--  (paying rent on Flat 203) AND the society maintainer ended up
--  with only maintainer-side access — their lease/payment pages
--  became unreachable.
--
--  Fix: store the maintainer credential in a SEPARATE column. At
--  login, auth-service tries user_password first; on miss, falls
--  back to maintainer_password — and when THAT matches, the issued
--  JWT carries role=MAINTAINER for that session instead of the
--  user's stored user_role. Same person, two login modes,
--  determined by which password they enter.
--
--  Nullable so existing rows (users who aren't maintainers of any
--  building) don't carry the column. Promotion sets the value;
--  password-reset never touches it.
-- ─────────────────────────────────────────────────────────────────

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE user_details_table ADD (maintainer_password VARCHAR2(255))';
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-01430 'column being added already exists in table' is fine.
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/
