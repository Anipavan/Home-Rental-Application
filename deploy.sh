#!/bin/bash
# ─────────────────────────────────────────────────────────────────
#  deploy.sh — backward-compat wrapper.
#
#  The old deploy.sh referenced compose.bootstrap.yml + tune.yml,
#  both of which were merged into docker-compose-prod.yml. This
#  file now forwards to the new deploy-prod.sh so any existing
#  cron jobs or muscle-memory calls to `./deploy.sh` keep working.
#
#  New scripts / operators should call deploy-prod.sh directly.
# ─────────────────────────────────────────────────────────────────

set -euo pipefail
cd "$(dirname "$0")"

echo "→ deploy.sh is now a wrapper — forwarding to deploy-prod.sh"
exec ./deploy-prod.sh "${@:-up -d --force-recreate}"
