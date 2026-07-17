#!/bin/bash
set -euo pipefail
cd /opt/anirudhhomes
git pull origin master

docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f compose.bootstrap.yml \
  -f docker-compose.tune.yml \
  build "$@"

docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f compose.bootstrap.yml \
  -f docker-compose.tune.yml \
  up -d --force-recreate "$@"

docker compose logs --tail=80 -f auth-service
