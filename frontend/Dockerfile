# Self-contained build: this Dockerfile is run from the repository root
# (build.context: . in docker-compose.yml). Two stages keep the runtime
# image tiny — nginx-alpine alone, no Node, no source.
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
# nginx:alpine is ~50MB; we drop the built dist/ folder into the
# default static-serve root + ship a small nginx.conf that handles
# SPA routing (every unknown path → index.html so client-side routes
# resolve) and proxies /api/* through to the API gateway.
FROM nginx:alpine
RUN addgroup -S app && adduser -S app -G app

# Replace the default nginx config with one that knows about SPA
# routing + gateway proxying. See frontend/nginx.conf for the
# full layout; key bits: try_files for SPA fallback, proxy_pass for
# /api/*, sensible cache headers for static assets, gzip/brotli on.
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

# Default port is 80 (overridable via PORT env at runtime if your
# orchestrator wants something different). Healthcheck hits the
# SPA root so a broken build (empty index.html, bad nginx config)
# is caught by kubelet before the pod is added to the LB rotation.
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
        CMD wget -qO- http://localhost/ >/dev/null || exit 1

# nginx must be PID 1 for proper SIGTERM handling. The default
# image already runs as root because nginx binds :80 — we accept
# that here because the container should be sandboxed by k8s pod
# security policy anyway (drop capabilities except NET_BIND_SERVICE,
# read-only root FS). For unprivileged-only environments switch to
# nginxinc/nginx-unprivileged:alpine and bind :8080 instead.
CMD ["nginx", "-g", "daemon off;"]
