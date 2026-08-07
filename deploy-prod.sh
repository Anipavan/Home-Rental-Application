#!/bin/bash
# ─────────────────────────────────────────────────────────────────
#  deploy-prod.sh — production deploy wrapper for Anirudh Homes.
#
#  Runs `git pull` on the droplet, then brings the prod stack up
#  or down using the merged compose files.
#
#  Usage on the droplet (from /opt/anirudhhomes):
#    ./deploy-prod.sh up -d              # first deploy or restart
#    ./deploy-prod.sh up -d --build      # after code changes
#    ./deploy-prod.sh build auth-service # rebuild one service
#    ./deploy-prod.sh down               # stop everything
#    ./deploy-prod.sh logs -f api-gateway
#    ./deploy-prod.sh ps                 # show status
#
#  Passes every argument through to `docker compose`, so anything
#  you'd type after `docker compose` works here.
# ─────────────────────────────────────────────────────────────────

set -euo pipefail

# Only pull when the operator explicitly asked for a rebuild/up
# with build; otherwise a stray `git pull` on every `logs` call
# is noisy and slow. The typical deploy flow (up + build) still
# gets fresh code because `up -d --build` implies a full pull.
case "${1:-}" in
  up|build|restart)
    git pull origin master
    ;;
esac

exec docker compose \
  --env-file .env-prod \
  -f docker-compose-base.yml \
  -f docker-compose-prod.yml \
  "$@"
