# Production Readiness — Anirudh Homes Rental Platform

Status: **NOT production-ready yet.** This document catalogs the known
gaps, what's been fixed in this branch, and what's left before the
platform can safely face real users + real payments.

The fixes below are sequenced by **deploy-day blast radius**. P0 items
will break or expose the platform within hours of going live. P1 items
will erode reliability or auditability within the first month. P2
items are housekeeping the platform can ship without but should be
scheduled in the first quarter.

---

## ✅ Fixed in this branch

1. **Hardcoded production password leak** — `ComplanceService`'s
   `application.yaml` had `password: ${DB_PASSWORD:Pavan@123}`. The
   literal `Pavan@123` was visible in the public repo. Swapped to
   `CHANGE_ME_LOCAL_DEV_DB_PLACEHOLDER` (the same shape every other
   service uses), and the `SecretsBootstrapValidator` will refuse to
   start under a non-dev profile when the placeholder leaks through.

2. **Bank-account PII at rest** — `bank_accounts.account_number` was
   stored in plaintext. Added a `@Converter`-based
   `EncryptedStringConverter` that wraps writes in AES-256-GCM and
   transparently decrypts on read. Key is sourced from
   `app.encryption.key` (env: `PII_ENCRYPTION_KEY`). Backward
   compatible — legacy plaintext rows are returned as-is so the
   converter can roll in without a back-fill migration.
   Added `app.encryption.key` to the
   `SecretsBootstrapValidator.SENSITIVE_KEYS` list.

3. **Production profile files** — Every JPA-backed Spring service +
   the api-gateway now has an `application-prod.yaml` that:
   - Sets `spring.jpa.hibernate.ddl-auto: validate` (was `update`).
     Prod schema changes must come from explicit migrations, not
     Hibernate's diff guess.
   - Sets `spring.jpa.show-sql: false`.
   - Trims `management.endpoints.web.exposure.include` to
     `health,info,metrics,prometheus` (drops `env` + `loggers`,
     both of which can leak property keys or be used to enable
     DEBUG logging that dumps sensitive payloads).
   - Sets `management.endpoint.health.show-details: never` so
     anonymous k8s liveness/readiness probes don't see DB / Kafka
     / disk internals.

   Activate with `SPRING_PROFILES_ACTIVE=prod` (or
   `ACTIVE_PROFILE=prod` in the Docker images).

---

## ⚠️ P0 — required before any traffic touches the box

### 1. Replace Hibernate auto-DDL with a real migration tool
Eight services still default to `ddl-auto: update` (the `prod`
profile overrides this — but `prod` must be active everywhere).
Even with `validate`, schema changes get blocked at startup if
the entity layer drifts. Adopt **Liquibase or Flyway**:

- One changelog per service, checked into `src/main/resources/db/migration/`.
- Add the corresponding starter (`spring-boot-starter-flyway` or
  `liquibase-core`).
- Convert every existing entity → an initial baseline migration
  (`V0001__baseline.sql`), generated from the current Hibernate
  schema export.
- Future schema work = new migration file + bump entity = atomic.

### 2. Externalise every secret
The `SecretsBootstrapValidator` will refuse to start under prod
when these env vars aren't set:

| Env var | Used by |
|---------|---------|
| `DB_PASSWORD` | every service with Oracle |
| `JWT_SECRET` | auth-service, api-gateway |
| `INTERNAL_AUTH_SECRET` | every backend service |
| `PII_ENCRYPTION_KEY` | user-service (bank account encryption) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | payment-service |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | payment-service |
| `EUREKA_PASSWORD` | every service |
| `CORS_ALLOWED_ORIGINS` | api-gateway |
| `FRONTEND_URL` | notification-service (sign-in / reset links) |

Use Kubernetes secrets (`kubectl create secret generic ...`) or a
secrets manager (Vault, AWS Secrets Manager, GCP Secret Manager).
**Never** check real values into the repo or pass via plain env-var
files.

### 3. CORS allowlist
`CORS_ALLOWED_ORIGINS` must be set to the real production SPA host
(e.g. `https://anirudhhomes.in,https://www.anirudhhomes.in`). The default
in `api-gateway/application.yaml` is `http://localhost:4200` — if
that survives into prod, every browser request from the real SPA
gets a 403 from the gateway's CORS filter.

### 4. HTTPS / TLS termination
Repo has no TLS config because TLS termination is expected at the
ingress / load-balancer layer (cloud LB, nginx, Traefik). Confirm:
- All public endpoints reachable only via HTTPS.
- `Strict-Transport-Security` (HSTS) header set with at least a
  6-month `max-age` and `includeSubDomains`.
- HTTP → HTTPS redirect at the ingress (not relying on the SPA).
- Internal service-to-service traffic also TLS where it crosses
  trust boundaries (multi-AZ, multi-cluster, etc.).

### 5. Frontend hardening
Before the SPA build is served:
- **CSP** — Content-Security-Policy header restricting `script-src`,
  `connect-src`, `img-src`, etc. Especially `connect-src` should
  include only the gateway origin + analytics.
- **`X-Frame-Options: DENY`** — clickjacking protection.
- **`X-Content-Type-Options: nosniff`**.
- **`Referrer-Policy: strict-origin-when-cross-origin`**.
- Build with `NODE_ENV=production`, source-maps disabled (or
  uploaded only to error-tracking, not served publicly).
- Set `VITE_API_BASE_URL` to the absolute prod gateway URL, not
  the Vite-proxy `/api/...` path.

### 6. Razorpay / Stripe webhook signature verification
Already implemented in payment-service. Before going live:
- Replace test-mode keys (`rzp_test_*` / `sk_test_*`) with live
  keys via env vars.
- Test webhook delivery against the prod gateway URL.
- Confirm the webhook idempotency table
  (`processed_webhooks`) is being populated (replay defense).

---

## ⚠️ P1 — fix in the first month

### 7. Structured (JSON) logging
Every service logs plaintext today. For log-aggregation (ELK /
Loki / Datadog / CloudWatch Logs Insights), add a
`logback-spring.xml` per service with the
`logstash-logback-encoder` dependency. Include MDC keys for
`traceId`, `spanId`, `userId` (when known), and `requestId`.

### 8. Distributed tracing
The log pattern already references `%X{traceId}` / `%X{spanId}`
but no tracer is configured. Add `micrometer-tracing-bridge-otel`
+ `opentelemetry-exporter-otlp` and a collector deployment.
Trace HTTP, Feign, JDBC, and Kafka spans. The gateway should
propagate the trace header to every downstream service.

### 9. Health probe wiring at k8s
Every service has `/actuator/health/liveness` and
`/actuator/health/readiness` enabled. K8s manifests should map
these to `livenessProbe.httpGet.path` and
`readinessProbe.httpGet.path`. Liveness on a fast endpoint;
readiness can include DB / Kafka checks (let the pod fall out of
the load-balancer when its dependencies are unhealthy).

### 10. Backups + DR
- Oracle: Data Pump + log-shipping to a warm-standby region.
- MongoDB (notification-service): `mongodump` cron + replica-set
  with 3 voting members across AZs.
- Document blobs (`uploads/` on the document-service host): move
  to S3-compatible object storage with versioning + lifecycle.
- Verify backup restoration end-to-end at least monthly.

### 11. Rate limits per user
The gateway `RateLimitFilter` is keyed on the path-level bucket
already. Add per-user buckets for `/auth/login`, `/auth/forgot-
password`, `/payments/{id}/initiate`, `/properties/flats/near` so
a single authenticated user can't burn through the per-path
budget for everyone.

### 12. Audit log of sensitive mutations
Many controllers log via `@Slf4j` (good), but security-relevant
events (login, password reset, role change, lease termination,
cash payment recorded, bank account updated) should go to a
**dedicated, append-only** audit log channel — separate index in
your log aggregator, longer retention, write-only by the app
identity. Helps post-incident review.

### 13. Data retention + deletion
Implement GDPR / India DPDP "right to be forgotten":
- Soft-delete already exists on `User.isDeleted`. Add a scheduled
  job that hard-deletes after the legal retention window (typically
  30-90 days post-soft-delete for non-financial data; financial
  records like rent invoices have a separate 7-year retention).
- Anonymise (don't drop) Payment / Lease history when the
  underlying tenant is forgotten — preserves owner audit trail.

---

## 📋 P2 — schedule in the first quarter

### 14. Container hardening (incremental)
Current Dockerfiles already do:
- Multi-stage build ✓
- Non-root `USER app` ✓
- Alpine JRE base ✓
- HEALTHCHECK directive ✓

Improvements:
- Read-only root filesystem (`--read-only` + tmpfs for /tmp).
- Drop all Linux capabilities except `NET_BIND_SERVICE` (and only
  if you bind <1024 inside the container — k8s usually doesn't).
- Run a vulnerability scanner (Trivy, Snyk) in CI; fail the build
  on HIGH/CRITICAL CVEs.
- Pin JRE base image by digest (`eclipse-temurin:21-jre-alpine@sha256:...`).

### 15. Real test coverage
Each service has unit tests. Coverage:
- Integration tests against an in-memory or Testcontainers DB —
  catch JPA mapping regressions before they hit a real schema.
- Contract tests for inter-service Kafka events (Pact / Spring
  Cloud Contract).
- E2E happy-path: register → list flat → assign tenant → pay rent
  → vacate → rent settled. Cypress / Playwright against a
  docker-compose stack.

### 16. SLO / alerting
Define SLOs per critical user journey (sign-in, payment, listing
fetch). Wire Prometheus alerts to:
- Error rate > 1% over 5 min.
- p95 latency > 1.5s.
- DB connection pool saturated.
- Kafka consumer lag growing on any topic.
- Eureka registration loss.

### 17. Multi-region readiness
- Stateful services (Oracle, MongoDB, Kafka) need explicit
  multi-region replication strategy.
- Document blob storage: cross-region replication.
- DNS failover (Route53 / Cloudflare) with health-based routing.
- Test region failover at least quarterly.

### 18. Cost / capacity planning
- Run a load test that mirrors expected peak traffic (1st-of-month
  rent payment burst).
- Tune Hikari pool sizes, JVM heap, k8s requests/limits per service
  based on the load test output.
- Set up cost dashboards before launching.

---

## Verification commands

After applying secret env vars + redeploying with prod profile:

```bash
# Each service should respond UP — readiness should reflect DB + Kafka readiness
curl -sf https://anirudhhomes.in/actuator/health/readiness
curl -sf https://anirudhhomes.in/actuator/health/liveness

# Secrets validator runs at startup — service should refuse to boot if
# any sensitive placeholder is still in play. Check the boot log for:
#   "SecretsBootstrapValidator: all sensitive secrets passed"

# Confirm management surface trimmed (env / loggers should 404 in prod)
curl -sf -o /dev/null -w "%{http_code}" https://anirudhhomes.in/actuator/env
# Expected: 404

# Confirm CORS only allows the real SPA origin
curl -I -H "Origin: https://evil.example" https://anirudhhomes.in/rentals/v1/properties/flats
# Expected: no Access-Control-Allow-Origin header (or 403)
```
