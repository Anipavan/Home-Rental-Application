# Self-contained build: this Dockerfile is run from the repository root
# (build.context: . in docker-compose-base.yml). Two stages keep the runtime
# image tiny — nginx-unprivileged-alpine alone, no Node, no source.
#
# Build args:
#   VITE_API_BASE_URL — the URL the SPA's axios client talks to. Default
#                       is /api/rentals/v1 (the Vite proxy path the dev
#                       server uses); prod deployments override this to
#                       the public gateway origin (e.g.
#                       https://api.anirudhhomes.in/rentals/v1).

# ----- build stage -----
FROM node:20-alpine AS build
WORKDIR /workspace

# 1. Install deps with the lockfile so the build is reproducible.
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund

# 2. Copy source + build. The vite.config.ts injects CSP + hidden
#    sourcemaps based on VITE_API_BASE_URL, so we pass that through.
COPY frontend/ ./
ARG VITE_API_BASE_URL=/api/rentals/v1
ARG VITE_CSP_CONNECT_SRC=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_CSP_CONNECT_SRC=$VITE_CSP_CONNECT_SRC
RUN npm run build

# ----- runtime stage -----
# nginxinc/nginx-unprivileged: same nginx binary as the official image,
# but runs as the `nginx` user (uid 101) instead of root and binds :8080
# by default. Container-escape severity drops materially — a bug in
# nginx that yields RCE lands in an unprivileged user, not root.
# Caddy in front of us forwards to port 8080 (see deploy/Caddyfile).
FROM nginxinc/nginx-unprivileged:alpine

# Replace the default nginx config with one that knows about SPA
# routing + gateway proxying. See frontend/nginx.conf for the
# full layout; key bits: try_files for SPA fallback, proxy_pass for
# /api/*, sensible cache headers for static assets, gzip/brotli on.
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

# Runs on 8080 (unprivileged nginx can't bind < 1024 without CAP_NET_BIND_SERVICE).
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
        CMD wget -qO- http://localhost:8080/ >/dev/null || exit 1

# nginx must be PID 1 for proper SIGTERM handling. Base image already
# runs as the non-root `nginx` user (uid 101).
CMD ["nginx", "-g", "daemon off;"]
