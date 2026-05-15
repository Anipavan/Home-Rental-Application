#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
#  Generate cryptographic secrets for the .env file.
#
#  Every service refuses to start under the `prod` profile when a
#  CHANGE_ME_* placeholder is still in play (SecretsBootstrapValidator
#  in auth-commons enforces this). This script generates each one
#  to the right strength and prints them in .env-compatible format.
#
#  Run on the droplet AFTER bootstrap-droplet.sh:
#    ./deploy/generate-secrets.sh > .env.secrets
#    cat .env.secrets   # review them
#
#  Then paste the values into your .env (which you copied from
#  .env.example). Or use envsubst to splice them in.
#
#  IMPORTANT: re-running this generates DIFFERENT secrets. Rotating
#  JWT_SECRET while the app is running invalidates all live sessions
#  (every browser must sign in again). Plan rotations during a quiet
#  window.
# ════════════════════════════════════════════════════════════════════

set -euo pipefail

# 256-bit base64 — JWT-signing keys, internal-auth HMAC keys, PII
# encryption keys. Anything that needs RFC-7515-grade entropy.
gen_256bit() {
    openssl rand -base64 32 | tr -d '\n'
}

# 32 bytes hex — for keys that prefer hex over base64 (some libs
# don't strip padding cleanly).
gen_32_hex() {
    openssl rand -hex 32
}

cat <<EOF
# ────────────────────────────────────────────────────────────────
#  Generated on $(date -u +"%Y-%m-%dT%H:%M:%SZ") by generate-secrets.sh
#
#  Paste these into your .env file (do NOT commit .env to git —
#  the repo's .gitignore already excludes it).
# ────────────────────────────────────────────────────────────────

# JWT signing key — auth-service signs tokens with this, api-gateway
# verifies. Rotating logs everyone out.
JWT_SECRET=$(gen_256bit)

# Internal-auth HMAC — every backend service refuses requests that
# don't carry an X-Internal-Auth-Sig stamped with this key. Closes
# the "anyone on the docker network can hit downstream services
# directly" hole.
INTERNAL_AUTH_SECRET=$(gen_256bit)

# AES-256-GCM key for bank_accounts.account_number encryption.
# Rotating means re-encrypting every bank account row.
PII_ENCRYPTION_KEY=$(gen_256bit)

# HMAC key for presigned document download URLs.
DOWNLOAD_URL_SECRET=$(gen_256bit)

# Eureka basic-auth password.
EUREKA_PASSWORD=$(gen_32_hex)

# Database password for the Oracle user (used by every JPA service).
DB_PASSWORD=$(gen_32_hex)
EOF
