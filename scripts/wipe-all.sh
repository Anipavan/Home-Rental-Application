#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  wipe-all.sh — full-state reset across every data store.
#
#  Removes every byte of demo / test / development data from the
#  deployment so the next signup starts from row 1 with zero history.
#  Safe to use as the "final test cycle → fresh production" cutover.
#
#  Wipes:
#    1. Oracle (HRA_APP schema)  — DELETE FROM every table; preserves
#       schema definitions + Flyway history rows. Sequences ARE NOT
#       reset (Oracle identity sequences are noisy to roll back);
#       this is intentional — new IDs just start at sequence current+1
#       instead of 1. Doesn't matter for UX.
#    2. MongoDB (HomeRentalDB)   — drop notifications + support tickets
#       + saved-search alerts collections. Indexes auto-recreate on
#       next service startup.
#    3. Uploaded files           — wipes /data/uploads/* (profile
#       photos, ID proofs, agreement PDFs, flat photos). Empties the
#       host volume that document-service writes to.
#    4. Kafka topics             — deletes + recreates the user-events,
#       payment-events, kyc-events topics so stale messages don't
#       replay on the next consumer rebalance.
#
#  Does NOT touch:
#    - JWT secrets, gateway secrets, Sandbox/Razorpay keys
#      (rotation lives in /scripts/rotate-secrets.sh)
#    - Flyway schema history (rolling those back gets us into the
#      pre-batch-1 dragons where Hibernate ddl-auto fights migrations)
#    - Caddyfile, docker-compose overrides, .env
#    - Container images themselves
#
#  ──────────────────────────────────────────────────────────────────
#  USAGE
#  ──────────────────────────────────────────────────────────────────
#    From /opt/anirudhhomes on the droplet:
#
#      # Default: confirmation prompts at every destructive step
#      bash scripts/wipe-all.sh
#
#      # Skip prompts (CI / scripted use only — read the comments first!)
#      WIPE_CONFIRM=YES bash scripts/wipe-all.sh
#
#      # Final pre-launch wipe (extra confirmations + summary)
#      bash scripts/wipe-all.sh --prod
#
#  ──────────────────────────────────────────────────────────────────
#  SAFETY
#  ──────────────────────────────────────────────────────────────────
#  Take a DigitalOcean snapshot BEFORE running this script. The wipe
#  is destructive and there is no built-in undo. The script also
#  refuses to run if it detects a /tmp/.wipe-all-running lock file,
#  to prevent two concurrent wipes from racing on Kafka topic deletes.
# ─────────────────────────────────────────────────────────────────────

set -uo pipefail

LOCK_FILE="/tmp/.wipe-all-running"
PROD_MODE="no"
SKIP_CONFIRMS="${WIPE_CONFIRM:-}"
for arg in "$@"; do
  case "$arg" in
    --prod) PROD_MODE="yes" ;;
    -h|--help)
      sed -n '2,60p' "$0"
      exit 0
      ;;
  esac
done

if [[ -f "$LOCK_FILE" ]]; then
  echo "✗ A wipe is already in progress (lock file at $LOCK_FILE)"
  echo "  If you know it's stale, remove it: rm $LOCK_FILE"
  exit 1
fi
trap 'rm -f "$LOCK_FILE"' EXIT
touch "$LOCK_FILE"

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.fix.yml"

# ─────────────────────────── helpers ───────────────────────────
confirm() {
  local prompt="$1"
  if [[ "$SKIP_CONFIRMS" == "YES" ]]; then
    echo "  · auto-confirmed: $prompt"
    return 0
  fi
  read -r -p "  ? $prompt [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]]
}

step() {
  echo ""
  echo "═══════════════════════════════════════════════════════════════════"
  echo "  $1"
  echo "═══════════════════════════════════════════════════════════════════"
}

# ─────────────────────────── banner ───────────────────────────
echo ""
echo "███████████████████████████████████████████████████████████████████"
echo "█                                                                 █"
echo "█   ANIRUDH HOMES — FULL DATA WIPE                                █"
echo "█                                                                 █"
echo "█   This will erase EVERY user, building, flat, lease, payment,   █"
echo "█   review, KYC record, notification, and uploaded file.          █"
echo "█                                                                 █"
if [[ "$PROD_MODE" == "yes" ]]; then
echo "█   MODE: --prod  (final pre-launch wipe)                         █"
else
echo "█   MODE: standard (between test cycles)                          █"
fi
echo "█                                                                 █"
echo "███████████████████████████████████████████████████████████████████"
echo ""

if [[ "$PROD_MODE" == "yes" ]]; then
  echo "You're about to do the FINAL pre-launch wipe. After this script"
  echo "the platform will be empty and ready for real users. Before you"
  echo "continue, confirm:"
  echo ""
  echo "  [ ] You have taken a DigitalOcean droplet snapshot in the"
  echo "      last 30 minutes."
  echo "  [ ] You have switched KYC_PROVIDER to SANDBOX (or MOCK if not"
  echo "      ready) in .env."
  echo "  [ ] You have switched PAYMENT_GATEWAY to razorpay (or stay on"
  echo "      mock for soft-launch) in .env."
  echo "  [ ] You are ready to publicly share anirudhhomes.in."
  echo ""
  confirm "All of the above checked, proceed with PROD wipe?" || {
    echo "Aborted."; exit 0;
  }
fi

confirm "Have you taken a DigitalOcean snapshot in the last 30 minutes?" || {
  echo ""
  echo "Take one now via the DigitalOcean control panel, then re-run."
  echo "Snapshots cost ₹0.05/GB/month and are your only undo for this."
  exit 0
}

# ─────────────── Step 1 — Oracle ───────────────
step "Step 1/4 — Oracle: TRUNCATE all HRA_APP tables"

if ! confirm "Wipe ALL rows from HRA_APP.* tables in Oracle?"; then
  echo "  · skipped Oracle wipe"
else
  $COMPOSE exec -T oracle-db sqlplus -S / as sysdba <<'SQL'
ALTER SESSION SET CONTAINER = FREEPDB1;
SET SERVEROUTPUT ON SIZE UNLIMITED
DECLARE
  v_table  VARCHAR2(128);
  v_count  NUMBER := 0;
BEGIN
  -- Disable referential integrity for the duration of the wipe so
  -- delete order doesn't matter (Oracle doesn't have CASCADE TRUNCATE).
  FOR c IN (
    SELECT constraint_name, table_name
      FROM user_constraints
     WHERE owner = 'HRA_APP'
       AND constraint_type = 'R'
       AND status = 'ENABLED'
  ) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRA_APP."' || c.table_name
      || '" DISABLE CONSTRAINT "' || c.constraint_name || '"';
  END LOOP;

  -- Delete every row from every HRA_APP table (skip Flyway history).
  FOR t IN (
    SELECT table_name
      FROM all_tables
     WHERE owner = 'HRA_APP'
       AND table_name NOT IN ('FLYWAY_SCHEMA_HISTORY')
  ) LOOP
    EXECUTE IMMEDIATE 'DELETE FROM HRA_APP."' || t.table_name || '"';
    v_count := v_count + SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('  · cleared ' || t.table_name
                         || ' (' || SQL%ROWCOUNT || ' rows)');
  END LOOP;

  -- Re-enable all FK constraints.
  FOR c IN (
    SELECT constraint_name, table_name
      FROM user_constraints
     WHERE owner = 'HRA_APP'
       AND constraint_type = 'R'
       AND status = 'DISABLED'
  ) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRA_APP."' || c.table_name
      || '" ENABLE CONSTRAINT "' || c.constraint_name || '"';
  END LOOP;

  COMMIT;
  DBMS_OUTPUT.PUT_LINE('');
  DBMS_OUTPUT.PUT_LINE('Total rows deleted: ' || v_count);
END;
/
EXIT;
SQL
fi

# ─────────────── Step 2 — MongoDB ───────────────
step "Step 2/4 — MongoDB: drop demo collections"

if ! confirm "Drop notifications + support_tickets + saved_searches collections?"; then
  echo "  · skipped Mongo wipe"
else
  $COMPOSE exec -T mongodb mongosh --quiet HomeRentalDB <<'JS'
const cols = db.getCollectionNames();
print("Collections present: " + cols.join(", "));
for (const c of cols) {
  // Skip internal system collections.
  if (c.startsWith("system.")) continue;
  const n = db.getCollection(c).countDocuments();
  db.getCollection(c).drop();
  print("  · dropped " + c + " (" + n + " docs)");
}
JS
fi

# ─────────────── Step 3 — Uploaded files ───────────────
step "Step 3/4 — Filesystem: wipe /data/uploads/*"

if [[ ! -d /data/uploads ]]; then
  echo "  · /data/uploads doesn't exist — nothing to wipe"
elif ! confirm "Delete EVERY file under /data/uploads/ (profile photos, ID proofs, etc.)?"; then
  echo "  · skipped file wipe"
else
  # Count first so the operator sees what they're deleting.
  COUNT=$(find /data/uploads -type f 2>/dev/null | wc -l)
  SIZE=$(du -sh /data/uploads 2>/dev/null | awk '{print $1}')
  echo "  · about to delete $COUNT files ($SIZE)"
  find /data/uploads -mindepth 1 -delete
  echo "  ✓ /data/uploads cleared"
fi

# ─────────────── Step 4 — Kafka topics ───────────────
step "Step 4/4 — Kafka: recreate user-events / payment-events / kyc-events topics"

if ! confirm "Delete + recreate the event topics so stale messages don't replay?"; then
  echo "  · skipped Kafka topic recreate"
else
  for TOPIC in user-events payment-events kyc-events lease-events maintenance-events review-events; do
    echo "  · recreating $TOPIC"
    $COMPOSE exec -T kafka kafka-topics --bootstrap-server localhost:9092 \
      --delete --topic "$TOPIC" --if-exists 2>/dev/null || true
    # Give zookeeper a beat to register the delete before recreate.
    sleep 1
    $COMPOSE exec -T kafka kafka-topics --bootstrap-server localhost:9092 \
      --create --topic "$TOPIC" --partitions 3 --replication-factor 1 \
      --if-not-exists 2>/dev/null || true
  done
  echo "  ✓ topics recreated"
fi

# ─────────────── Verification ───────────────
step "Verification — counts that should all be ZERO"

$COMPOSE exec -T oracle-db sqlplus -S / as sysdba <<'SQL' 2>/dev/null
ALTER SESSION SET CONTAINER = FREEPDB1;
SET LINESIZE 100
SET PAGESIZE 0
SET FEEDBACK OFF
SELECT 'users:        ' || COUNT(*) FROM hra_app.user_details_table;
SELECT 'buildings:    ' || COUNT(*) FROM hra_app.buildings;
SELECT 'flats:        ' || COUNT(*) FROM hra_app.flats;
SELECT 'payments:     ' || COUNT(*) FROM hra_app.payments;
EXIT;
SQL

echo ""
echo "███████████████████████████████████████████████████████████████████"
echo "█  WIPE COMPLETE                                                  █"
echo "███████████████████████████████████████████████████████████████████"
echo ""

if [[ "$PROD_MODE" == "yes" ]]; then
  echo "Final pre-launch checklist:"
  echo "  [ ] Restart all services so they pick up the empty event topics:"
  echo "      $COMPOSE restart auth-service user-service property-service \\"
  echo "                       payment-service kyc-service notification-service"
  echo "  [ ] Verify .env has KYC_PROVIDER + PAYMENT_GATEWAY set as you want"
  echo "  [ ] Hit https://anirudhhomes.in and confirm the landing page loads"
  echo "      with empty stats (0 homes, 0 cities)"
  echo "  [ ] Register your own account end-to-end"
  echo "  [ ] Manually update that user's role to ADMIN in the DB"
  echo "  [ ] You're live."
else
  echo "Next steps (test cycle):"
  echo "  1. Restart the back-office services so they re-consume empty topics:"
  echo "     $COMPOSE restart notification-service kyc-service payment-service"
  echo "  2. Register a fresh test user via the UI"
  echo "  3. Walk the full flow: KYC → list property → assign tenant → pay rent → review"
  echo "  4. Bugs found here, fix them, re-run THIS script, retest."
  echo "  5. When you're happy, run 'bash scripts/wipe-all.sh --prod' for the"
  echo "     real launch wipe."
fi
echo ""
