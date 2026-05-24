#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  seed-pending-payments.sh
#
#  Inserts one PENDING rent payment per assigned tenant from the
#  property seeder. Normally the {@code flat.occupied} Kafka event
#  fires when an owner clicks Assign, payment-service consumes it,
#  and creates the first invoice for the new tenant. The SQL-direct
#  property seeder (seed-properties-sql.sh) bypassed that event, so
#  the four tenants we assigned have no PENDING payment row — and
#  clicking "Pay rent" in the UI shows "You're all paid" because
#  the payments query finds nothing.
#
#  This script fixes that by writing one row to HRA_APP.payments per
#  tenant→flat lease, with:
#    - amount = monthly rent of the flat
#    - due_date = 1st of next month (matches the prod scheduler logic)
#    - status = PENDING
#
#  Re-running is safe — uses fixed primary keys (demo-pay-001..004)
#  with an existence check before insert.
#
#  Run from /opt/anirudhhomes:
#    bash scripts/seed-pending-payments.sh
# ─────────────────────────────────────────────────────────────────────

set -uo pipefail

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.fix.yml"

echo "═══════════════════════════════════════════════════════════════════"
echo "  Anirudh Homes — Pending Payments Seeder"
echo "═══════════════════════════════════════════════════════════════════"

$COMPOSE exec -T oracle-db sqlplus / as sysdba <<'SQL'
ALTER SESSION SET CONTAINER = FREEPDB1;
SET SERVEROUTPUT ON SIZE UNLIMITED
SET DEFINE OFF

DECLARE
  v_count   NUMBER;
  v_due     DATE := ADD_MONTHS(TRUNC(SYSDATE, 'MM'), 1);   -- 1st of NEXT month
  v_now     TIMESTAMP := SYSTIMESTAMP;

  -- Tenant/flat/owner/amount tuples for each lease we seeded.
  -- IDs come from the property seeder's expected outcomes:
  --   demo-flat-001 (#101, ₹18000)  ← tenant_dana (auth 8)  / owner_alice (5)
  --   demo-flat-009 (#201, ₹28000)  ← tenant_eli  (auth 9)  / owner_alice (5)
  --   demo-flat-013 (#101, ₹28000)  ← tenant_fran (auth 10) / owner_bob   (6)
  --   demo-flat-035 (#501, ₹45000)  ← tenant_gabe (auth 11) / owner_charlie (7)

  PROCEDURE upsert_payment(
      p_id       VARCHAR2,
      p_tenant   VARCHAR2,
      p_flat     VARCHAR2,
      p_owner    VARCHAR2,
      p_amount   NUMBER
  ) IS
  BEGIN
    SELECT COUNT(*) INTO v_count FROM hra_app.payments WHERE id = p_id;
    IF v_count > 0 THEN
      DBMS_OUTPUT.PUT_LINE('  · payment exists: ' || p_id || ' (skip)');
      RETURN;
    END IF;

    -- Also skip if this tenant already has any PENDING payment for this
    -- flat — covers the case where the user previously hit Assign via
    -- the UI and a Kafka event already created one.
    SELECT COUNT(*) INTO v_count
      FROM hra_app.payments
      WHERE tenant_id = p_tenant
        AND flat_id   = p_flat
        AND status    = 'PENDING';
    IF v_count > 0 THEN
      DBMS_OUTPUT.PUT_LINE('  · tenant ' || p_tenant ||
                           ' already has a PENDING payment on flat ' ||
                           p_flat || ' (skip)');
      RETURN;
    END IF;

    INSERT INTO hra_app.payments (
      id, tenant_id, flat_id, owner_id,
      amount, late_fee, total_amount,
      due_date, status,
      created_at, updated_at
    ) VALUES (
      p_id, p_tenant, p_flat, p_owner,
      p_amount, 0, p_amount,
      v_due, 'PENDING',
      v_now, v_now
    );
    DBMS_OUTPUT.PUT_LINE('  + payment: ' || p_id ||
                         ' tenant=' || p_tenant ||
                         ' flat=' || p_flat ||
                         ' amount=Rs.' || p_amount ||
                         ' due=' || TO_CHAR(v_due, 'YYYY-MM-DD'));
  END;

BEGIN
  DBMS_OUTPUT.PUT_LINE('Creating PENDING payments (due ' ||
                       TO_CHAR(v_due, 'DD-Mon-YYYY') || ')...');

  upsert_payment('demo-pay-001', '8',  'demo-flat-001', '5', 18000);
  upsert_payment('demo-pay-002', '9',  'demo-flat-009', '5', 28000);
  upsert_payment('demo-pay-003', '10', 'demo-flat-013', '6', 28000);
  upsert_payment('demo-pay-004', '11', 'demo-flat-035', '7', 45000);

  COMMIT;

  DBMS_OUTPUT.PUT_LINE('');
  SELECT COUNT(*) INTO v_count FROM hra_app.payments WHERE id LIKE 'demo-pay-%';
  DBMS_OUTPUT.PUT_LINE('Total demo payments now: ' || v_count);
  SELECT COUNT(*) INTO v_count FROM hra_app.payments WHERE status = 'PENDING';
  DBMS_OUTPUT.PUT_LINE('All PENDING payments:    ' || v_count);
END;
/
EXIT;
SQL

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "  Done. Sign in as tenant_dana / Demo@2026! and click 'Pay rent'."
echo "  You should now see the payment method picker (UPI / Card / etc.)"
echo "═══════════════════════════════════════════════════════════════════"
