-- ─────────────────────────────────────────────────────────────────
--  V7 — Dual approval for maintainer reassignment.
--
--  Background: the original V6 model only required the owner to
--  approve a MAINTAINER claim. That works for "this building has no
--  maintainer yet" — but for the REPLACE case (someone applies to
--  take over from a current maintainer), single-party approval is a
--  fraud vector. If the owner's account is compromised the attacker
--  can silently swap the maintainer and drain the next month of UPI
--  collections.
--
--  Fix: when a MAINTAINER claim targets a building that ALREADY has
--  an active maintainer, both the OWNER and the CURRENT MAINTAINER
--  must approve the swap. Either side rejecting kills the claim.
--
--  Schema additions:
--    requires_dual_approval        NUMBER(1)  DEFAULT 0   — set 1 by
--      the service layer at create time when a current maintainer
--      exists. Stored as a column (not derived) so it stays stable
--      even if the current maintainer changes mid-flight.
--    owner_decided_at              TIMESTAMP              — set when
--      the owner clicks Approve / Reject.
--    owner_decided_by_user_id      VARCHAR2(64)           — owner's
--      authUserId at the time of their decision.
--    maintainer_decided_at         TIMESTAMP              — current
--      maintainer's decision timestamp. Null when not yet acted.
--    maintainer_decided_by_user_id VARCHAR2(64)           — current
--      maintainer's authUserId at decision time.
--
--  The existing decided_at + decided_by_user_id columns from V6 are
--  kept and now reflect the FINAL decision (whichever party closed
--  the loop — approving second party or first to reject).
--
--  Backfill: existing PENDING claims default to requires_dual_approval
--  = 0 (V6 single-party behaviour). They can't have the dual model
--  retroactively imposed without notifying parties that don't exist
--  yet in this schema (the current maintainer at create time).
-- ─────────────────────────────────────────────────────────────────

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims '
     || 'ADD (requires_dual_approval NUMBER(1) DEFAULT 0 NOT NULL)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims ADD (owner_decided_at TIMESTAMP)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims ADD (owner_decided_by_user_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims ADD (maintainer_decided_at TIMESTAMP)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE membership_claims ADD (maintainer_decided_by_user_id VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/
