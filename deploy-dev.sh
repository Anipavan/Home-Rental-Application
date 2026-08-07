#!/bin/bash
# ─────────────────────────────────────────────────────────────────
#  deploy-dev.sh — local development wrapper.
#
#  Usage:
#    ./deploy-dev.sh up -d --build
#    ./deploy-dev.sh logs -f api-gateway
#    ./deploy-dev.sh down
#
#  Passes every arg through to `docker compose`.
# ─────────────────────────────────────────────────────────────────

set -euo pipefail

exec docker compose \
  --env-file .env-dev \
  -f docker-compose-base.yml \
  -f docker-compose-dev.yml \
  "$@"
