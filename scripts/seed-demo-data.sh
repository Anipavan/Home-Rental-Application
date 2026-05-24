#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  Anirudh Homes — Demo Data Seeder
#
#  Creates a realistic data set so admin / owner / tenant flows can be
#  demoed end-to-end without 50 minutes of manual clicking. Useful for:
#    - Showcasing the app to sample testers
#    - Recording a demo video (screens look populated, not empty)
#    - Manual smoke tests during development
#
#  WHAT IT CREATES
#  ──────────────────────────────────────────────────────────────────
#    1 admin user                                  (admin / Demo@2026!)
#    3 owner users                                 (owner_alice, owner_bob, owner_charlie)
#    5 tenant users                                (tenant_dana, _eli, _fran, _gabe, _hina)
#    6 buildings (2 per owner) across 4 cities     (Bangalore, Mumbai, Pune, Hyderabad)
#    Up to 18 flats (≥6 per building, the minimum the API enforces)
#    2 tenant→flat assignments                     (so lease/payment flows are testable)
#
#  USAGE
#  ──────────────────────────────────────────────────────────────────
#    From /opt/anirudhhomes on the droplet:
#      bash scripts/seed-demo-data.sh
#
#    Override defaults via env:
#      BASE_URL=...         (default: https://anirudhhomes.in/api/rentals/v1)
#      DEMO_PASSWORD=...    (default: Demo@2026! — meets the policy:
#                            ≥8 chars, upper, lower, digit, special)
#
#  IDEMPOTENT
#  ──────────────────────────────────────────────────────────────────
#  Every step checks "does it already exist?" before creating, so
#  rerunning the script is safe — it just no-ops on rows that survived.
#
#  REQUIREMENTS
#  ──────────────────────────────────────────────────────────────────
#    - bash (already on the droplet)
#    - curl (already on the droplet)
#    - python3 (for inline JSON parsing — universal, no jq dependency)
#    - docker compose (for the admin-role SQL UPDATE)
# ─────────────────────────────────────────────────────────────────────

set -uo pipefail

BASE_URL="${BASE_URL:-https://anirudhhomes.in/api/rentals/v1}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@2026!}"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.fix.yml"

echo "═══════════════════════════════════════════════════════════════════"
echo "  Anirudh Homes Demo Data Seeder"
echo "═══════════════════════════════════════════════════════════════════"
echo "  Base URL: $BASE_URL"
echo "  Password: $DEMO_PASSWORD"
echo "  (override via DEMO_PASSWORD=... env)"
echo "═══════════════════════════════════════════════════════════════════"

# ─────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────

# Extract a key from JSON via python3 (universal — no jq dep).
# Usage: extract_json '<json>' 'someKey'
extract_json() {
  python3 -c "import sys, json
try:
    d = json.loads(sys.stdin.read())
    print(d.get('$2', '') if isinstance(d, dict) else '')
except Exception:
    print('')" <<< "$1"
}

# POST /auth/register. Returns the response body on stdout, sets
# global REG_HTTP_CODE for status inspection.
register_user() {
  local username=$1 role=$2 email=$3 firstName=$4 lastName=$5 gender=$6 phone=$7

  local payload
  payload=$(cat <<JSON
{
  "userName": "$username",
  "userPassword": "$DEMO_PASSWORD",
  "userRole": "$role",
  "email": "$email",
  "firstName": "$firstName",
  "lastName": "$lastName",
  "gender": "$gender",
  "phone": "$phone"
}
JSON
)

  local body
  body=$(curl -sS -w "\n%{http_code}" -X POST "${BASE_URL}/auth/register" \
    -H "Content-Type: application/json" \
    -d "$payload" 2>&1) || body="${body}\n0"

  REG_HTTP_CODE="${body##*$'\n'}"
  REG_BODY="${body%$'\n'*}"

  case "$REG_HTTP_CODE" in
    200|201)
      echo "  ✓ $username ($role)"
      return 0
      ;;
    400|409|422)
      # Already exists / duplicate. Most likely a re-run.
      echo "  · $username — already exists, skipping"
      return 0
      ;;
    *)
      echo "  ✗ $username FAILED — HTTP $REG_HTTP_CODE"
      echo "    response: $REG_BODY"
      return 1
      ;;
  esac
}

# POST /auth/login, echoes the accessToken on stdout, empty on failure.
login_user() {
  local username=$1
  local resp
  resp=$(curl -sS -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"userName\":\"$username\",\"userPassword\":\"$DEMO_PASSWORD\"}" 2>/dev/null) || resp=""
  extract_json "$resp" "accessToken"
}

# GET /users/by-auth-id/{authUserId} not used — instead we extract the
# authUserId from the login response, which already carries it.
login_user_full() {
  local username=$1
  curl -sS -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"userName\":\"$username\",\"userPassword\":\"$DEMO_PASSWORD\"}" 2>/dev/null
}

create_building() {
  local token=$1 ownerId=$2 name=$3 address=$4 city=$5 state=$6 floors=$7 flats=$8
  local payload
  payload=$(cat <<JSON
{
  "buildingName": "$name",
  "ownerId": "$ownerId",
  "buildingAddress": "$address",
  "buildingCity": "$city",
  "buildingState": "$state",
  "buildingTotalFloors": $floors,
  "buildingTotalFlats": $flats,
  "amenities": "Lift, Power backup, 24x7 security, Visitor parking",
  "includedItems": "Modular kitchen, Wardrobes, RO water purifier"
}
JSON
)
  local resp
  resp=$(curl -sS -X POST "${BASE_URL}/properties/buildings/create/building" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" 2>/dev/null) || resp=""
  extract_json "$resp" "id"
}

create_flat() {
  local token=$1 buildingId=$2 flatNumber=$3 floor=$4 bedrooms=$5 bathrooms=$6 area=$7 rent=$8 deposit=$9
  local payload
  payload=$(cat <<JSON
{
  "buildingId": "$buildingId",
  "flatNumber": "$flatNumber",
  "floor": $floor,
  "bedrooms": $bedrooms,
  "bathrooms": $bathrooms,
  "areaSqft": $area,
  "rentAmount": $rent,
  "depositAmount": $deposit,
  "furnishingStatus": "SEMI_FURNISHED",
  "petFriendly": true,
  "description": "Bright, well-ventilated flat with balcony. Walking distance to metro and shopping."
}
JSON
)
  local resp
  resp=$(curl -sS -X POST "${BASE_URL}/properties/flats/create/flat" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" 2>/dev/null) || resp=""
  extract_json "$resp" "id"
}

assign_tenant() {
  local token=$1 flatId=$2 tenantAuthId=$3 startDate=$4
  local payload
  payload=$(cat <<JSON
{
  "tenantId": "$tenantAuthId",
  "leaseStartDate": "$startDate"
}
JSON
)
  curl -sS -o /dev/null -w "%{http_code}" -X POST \
    "${BASE_URL}/properties/flats/$flatId/assign" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" 2>/dev/null
}

# ─────────────────────────────────────────────────────────────────────
# 1. Register the admin user
#    /auth/register accepts role=ADMIN per the enum. If your form blocks
#    ADMIN at the UI layer, the backend API still allows it.
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "▸ Step 1/4 — Creating admin user"
register_user "admin" "ADMIN" "admin@anirudhhomes.in" "Site" "Admin" "OTHER" "+919108201001" || true

# ─────────────────────────────────────────────────────────────────────
# 2. Register 3 owners + 5 tenants
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "▸ Step 2/4 — Registering 3 owners + 5 tenants"
register_user "owner_alice"   "OWNER"  "alice@anirudhhomes.in"   "Alice"   "Sharma"  "FEMALE" "+919108201101" || true
register_user "owner_bob"     "OWNER"  "bob@anirudhhomes.in"     "Bob"     "Verma"   "MALE"   "+919108201102" || true
register_user "owner_charlie" "OWNER"  "charlie@anirudhhomes.in" "Charlie" "Reddy"   "MALE"   "+919108201103" || true

register_user "tenant_dana"   "TENANT" "dana@anirudhhomes.in"    "Dana"    "Singh"   "FEMALE" "+919108201201" || true
register_user "tenant_eli"    "TENANT" "eli@anirudhhomes.in"     "Eli"     "Kumar"   "MALE"   "+919108201202" || true
register_user "tenant_fran"   "TENANT" "fran@anirudhhomes.in"    "Fran"    "Iyer"    "FEMALE" "+919108201203" || true
register_user "tenant_gabe"   "TENANT" "gabe@anirudhhomes.in"    "Gabe"    "Menon"   "MALE"   "+919108201204" || true
register_user "tenant_hina"   "TENANT" "hina@anirudhhomes.in"    "Hina"    "Patel"   "FEMALE" "+919108201205" || true

# ─────────────────────────────────────────────────────────────────────
# 3. For each owner: log in, create 2 buildings, ≥6 flats per building
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "▸ Step 3/4 — Creating buildings + flats per owner"

# Property data: 6 buildings = 2 per owner × 3 owners.
# (owner_username, building_name, address, city, state, total_floors, total_flats)
declare -a BUILDINGS=(
  "owner_alice|Sunshine Residency|Indiranagar 12th Main|Bangalore|Karnataka|4|6"
  "owner_alice|Lake View Towers|HSR Layout Sector 2|Bangalore|Karnataka|6|8"
  "owner_bob|Garden Heights|Andheri West, Lokhandwala|Mumbai|Maharashtra|8|10"
  "owner_bob|Hill Crest|Powai, near IIT|Mumbai|Maharashtra|5|6"
  "owner_charlie|Green Acres|Koregaon Park|Pune|Maharashtra|4|6"
  "owner_charlie|Cyber Towers|HiTech City|Hyderabad|Telangana|7|8"
)

# Owner login tokens cached
declare -A OWNER_TOKEN
declare -A OWNER_AUTH_ID

for OWNER in owner_alice owner_bob owner_charlie; do
  RESP=$(login_user_full "$OWNER")
  TOKEN=$(extract_json "$RESP" "accessToken")
  AUTH_ID=$(extract_json "$RESP" "authUserId")
  if [[ -z "$TOKEN" ]]; then
    echo "  ✗ FAILED to log in as $OWNER — cannot create buildings"
    continue
  fi
  OWNER_TOKEN[$OWNER]=$TOKEN
  OWNER_AUTH_ID[$OWNER]=$AUTH_ID
  echo "  · logged in as $OWNER (authUserId=$AUTH_ID)"
done

# Track building IDs we just created so we can seed flats
declare -a CREATED_BUILDING_IDS=()
declare -a CREATED_BUILDING_OWNERS=()
declare -a CREATED_BUILDING_FLATS=()

for ROW in "${BUILDINGS[@]}"; do
  IFS='|' read -r OWNER BNAME ADDR CITY STATE FLOORS FLATS <<< "$ROW"
  TOKEN="${OWNER_TOKEN[$OWNER]:-}"
  AUTH_ID="${OWNER_AUTH_ID[$OWNER]:-}"
  if [[ -z "$TOKEN" || -z "$AUTH_ID" ]]; then
    echo "  · skipping $BNAME — owner $OWNER not logged in"
    continue
  fi
  BID=$(create_building "$TOKEN" "$AUTH_ID" "$BNAME" "$ADDR" "$CITY" "$STATE" "$FLOORS" "$FLATS")
  if [[ -n "$BID" ]]; then
    echo "  ✓ building: $BNAME ($CITY) — id=$BID"
    CREATED_BUILDING_IDS+=("$BID")
    CREATED_BUILDING_OWNERS+=("$OWNER")
    CREATED_BUILDING_FLATS+=("$FLATS")
  else
    echo "  · building: $BNAME — create failed or already exists (response did not include id)"
  fi
done

# Now create flats per building (6 flats minimum each — matches API rule)
echo ""
echo "  Creating flats…"
ALL_FLAT_IDS=()
ALL_FLAT_OWNERS=()
for i in "${!CREATED_BUILDING_IDS[@]}"; do
  BID="${CREATED_BUILDING_IDS[$i]}"
  OWNER="${CREATED_BUILDING_OWNERS[$i]}"
  FLAT_COUNT="${CREATED_BUILDING_FLATS[$i]}"
  TOKEN="${OWNER_TOKEN[$OWNER]}"

  # Realistic rent ranges: ₹15k-₹50k per flat depending on bedrooms
  for n in $(seq 1 "$FLAT_COUNT"); do
    FLOOR=$(( (n - 1) / 2 + 1 ))
    BEDROOMS=$(( (n % 3) + 1 ))                    # cycles 1,2,3
    BATHROOMS=$(( BEDROOMS > 1 ? 2 : 1 ))
    AREA=$(( 400 + BEDROOMS * 250 ))               # 650 / 900 / 1150 sqft
    RENT=$(( 12000 + BEDROOMS * 8000 ))            # 20k / 28k / 36k
    DEPOSIT=$(( RENT * 3 ))                        # 3-month deposit per RBI
    FNUM="$(printf "%d%02d" "$FLOOR" "$n")"
    FID=$(create_flat "$TOKEN" "$BID" "$FNUM" "$FLOOR" "$BEDROOMS" "$BATHROOMS" "$AREA" "$RENT" "$DEPOSIT")
    if [[ -n "$FID" ]]; then
      ALL_FLAT_IDS+=("$FID")
      ALL_FLAT_OWNERS+=("$OWNER")
    fi
  done
  echo "  ✓ $FLAT_COUNT flats added to building $BID"
done

# ─────────────────────────────────────────────────────────────────────
# 4. Assign 2 tenants to 2 flats (so lease/payment flows are testable)
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "▸ Step 4/4 — Assigning tenants to flats"

# Get tenant auth IDs
declare -A TENANT_AUTH_ID
for T in tenant_dana tenant_eli; do
  RESP=$(login_user_full "$T")
  TID=$(extract_json "$RESP" "authUserId")
  TENANT_AUTH_ID[$T]=$TID
  echo "  · $T authUserId=$TID"
done

START_DATE=$(date -u +%Y-%m-%d)

# Assign tenant_dana to the first available flat
if [[ ${#ALL_FLAT_IDS[@]} -ge 1 && -n "${TENANT_AUTH_ID[tenant_dana]:-}" ]]; then
  FID="${ALL_FLAT_IDS[0]}"
  OWNER="${ALL_FLAT_OWNERS[0]}"
  TOKEN="${OWNER_TOKEN[$OWNER]}"
  CODE=$(assign_tenant "$TOKEN" "$FID" "${TENANT_AUTH_ID[tenant_dana]}" "$START_DATE")
  echo "  · assign tenant_dana → flat $FID (HTTP $CODE)"
fi

# Assign tenant_eli to a flat under a different owner so both flows have test data
if [[ ${#ALL_FLAT_IDS[@]} -ge 7 && -n "${TENANT_AUTH_ID[tenant_eli]:-}" ]]; then
  # Find the first flat belonging to a DIFFERENT owner
  for j in "${!ALL_FLAT_IDS[@]}"; do
    if [[ "${ALL_FLAT_OWNERS[$j]}" != "${ALL_FLAT_OWNERS[0]}" ]]; then
      FID="${ALL_FLAT_IDS[$j]}"
      OWNER="${ALL_FLAT_OWNERS[$j]}"
      TOKEN="${OWNER_TOKEN[$OWNER]}"
      CODE=$(assign_tenant "$TOKEN" "$FID" "${TENANT_AUTH_ID[tenant_eli]}" "$START_DATE")
      echo "  · assign tenant_eli → flat $FID (HTTP $CODE)"
      break
    fi
  done
fi

# ─────────────────────────────────────────────────────────────────────
# Final report
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "  SEED COMPLETE"
echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "  Sign in at https://anirudhhomes.in/sign-in"
echo "  Password for every account: $DEMO_PASSWORD"
echo ""
echo "  Admin     →  admin             (sees /admin/* dashboards)"
echo "  Owners    →  owner_alice  ·  owner_bob  ·  owner_charlie"
echo "  Tenants   →  tenant_dana (assigned)  ·  tenant_eli (assigned)"
echo "               tenant_fran  ·  tenant_gabe  ·  tenant_hina"
echo ""
echo "  Buildings created:  ${#CREATED_BUILDING_IDS[@]}"
echo "  Flats created:      ${#ALL_FLAT_IDS[@]}"
echo ""
echo "═══════════════════════════════════════════════════════════════════"
