# Home Rental Application

Spring Boot microservices platform for property owners and tenants.

## Topology

```
                  ┌─────────────────────────────────────┐
                  │   API Gateway   :8080  (Netty)      │
                  │   JWT validate · HMAC sign          │
                  └──────────────────┬──────────────────┘
         ┌────────────┬──────────────┼─────────────┬─────────────┐
         ▼            ▼              ▼             ▼             ▼
   Auth :9090   Property :8088  User :8089  Maintenance :8085  Payment :8084
         │            │              │             │             │
         └────────────┴──────┬───────┴─────────────┴─────────────┘
                             ▼ Kafka :9093 (6 topics)
                ┌────────────┴────────────┐
                ▼                         ▼
    Notification :8086          Analytics :8087
   (email/SMS/push,              (revenue/occupancy/
    Mongo)                        trends/maintenance,
                                  Oracle, Excel/PDF)

   Cross-cutting infra:
     Service Registry (Eureka)  :8761
     Config Server              :8888
     Shared libs: KafkaEvents, auth-commons
```

## Modules

| Module | Port | Tech | Purpose |
|--------|------|------|---------|
| `Service-Registry`     | 8761 | Eureka Server      | Service discovery |
| `config-server`        | 8888 | Spring Cloud Config | Centralised configuration |
| `api-gateway`          | 8080 | Spring Cloud Gateway (Netty) | Single ingress, JWT validation, HMAC signing, CORS, **per-route circuit breakers, /rentals/v1 versioning** |
| `auth-service`         | 9090 | Spring Boot + JWT  | Register, login, refresh tokens, RBAC, password reset |
| `property-service`     | 8088 | Spring Boot + JPA  | Buildings, flats, occupancy, images |
| `user-service`         | 8089 | Spring Boot + JPA  | User profiles, owners, emergency contacts |
| `payment-service`      | 8084 | Spring Boot + JPA  | Invoices, multi-method payments (UPI, cards, net-banking, wallets, bank transfer, cash), receipts |
| `maintenance-service`  | 8085 | Spring Boot + Mongo | Maintenance request workflow with state machine |
| `notification-service` | 8086 | Spring Boot + Mongo | Email/SMS/push fan-out from Kafka events |
| `analytics-service`    | 8087 | Spring Boot + JPA  | Aggregated metrics + Excel/PDF reports |
| `KafkaEvents`          | —    | Library            | Shared event DTOs and producer beans |
| `auth-commons`         | —    | Library            | Gateway-signature verification filter for downstream services |

## Quick start (full stack via Docker)

```bash
# 1. Build everything (jars used by the Dockerfiles)
mvn clean install -DskipTests

# 2. Bring up the whole platform
docker compose up -d

# 3. Watch the logs as services come online
docker compose logs -f
```

The compose file starts services in dependency order (infra → discovery+config → gateway → domain). Health-aware `depends_on` ensures each tier waits for its prerequisites.

Once everything is **UP**:

| URL | Use |
|-----|-----|
| `http://localhost:8761/`   | Eureka dashboard — every service should appear here |
| **`http://localhost:8080/swagger-ui.html`** | **Unified Swagger UI** — pick any service from the dropdown (top-right), click "Authorize" once with your JWT, then Try-It-Out across every service |
| `http://localhost:8080/aggregate/<service>/v3/api-docs` | Raw OpenAPI 3 spec for `<service>` (e.g. `auth-service`, `property-service`, …) |
| `http://localhost:8888/HRA-property-service/default` | Sample Config Server fetch |
| `http://localhost:9090/swagger-ui.html` | Direct Auth Service docs (and per-service ports for the others) |

## Quick start (local — no Docker)

Open one shell per service. Boot order matters:

```bash
# Terminal 1 — Eureka
cd Service-Registry && mvn -B spring-boot:run

# Terminal 2 — Config Server (waits for Eureka)
cd config-server && mvn -B spring-boot:run

# Terminal 3 — API Gateway
cd api-gateway && mvn -B spring-boot:run

# Terminals 4–9 — domain services (any order)
cd auth-service && mvn -B spring-boot:run
cd property-service && mvn -B spring-boot:run
cd user-service && mvn -B spring-boot:run
cd payment-service && mvn -B spring-boot:run
cd maintenance-service && mvn -B spring-boot:run
cd notification-service && INTERNAL_AUTH_ENABLED=true NOTIFICATION_DELIVERY=false mvn -B spring-boot:run
cd analytics-service && mvn -B spring-boot:run
```

You'll need Oracle XE / Free running on `1521` and Kafka on `9093` — see `docker compose up -d oracle-db mongodb kafka zookeeper` if you only want infra.

## End-to-end smoke test

After everything is up:

```bash
# 1. Register   (note: /rentals/v1/ — see "API Versioning" below)
curl -X POST http://localhost:8080/rentals/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "userName":"alice","userPassword":"Strong123","userRole":"TENANT",
    "firstName":"Alice","lastName":"Smith",
    "email":"alice@example.com","phone":"+919876543210",
    "gender":"FEMALE","dateOfBirth":"1995-04-12","address":"Bangalore"
  }'

# 2. Login → grab accessToken + refreshToken from the response
curl -X POST http://localhost:8080/rentals/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userName":"alice","password":"Strong123"}'

# 3. Use the access token on a protected endpoint
curl http://localhost:8080/rentals/v1/properties/buildings \
  -H "Authorization: Bearer <accessToken>"

# 4. Refresh when the access token expires
curl -X POST http://localhost:8080/rentals/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# 5. Logout
curl -X POST http://localhost:8080/rentals/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'
```

Direct hits to a service bypass the gateway and are blocked with `403 GATEWAY_REQUIRED`:

```bash
# Should fail with 403
curl http://localhost:9090/auth/login -H 'Content-Type: application/json' \
  -d '{"userName":"alice","password":"Strong123"}'
```

## API versioning, load balancing & resilience

### Versioning (URI-based, gateway-controlled)

The gateway accepts traffic on **two URI shapes** for every domain service:

| Shape | Status | Header set on response |
|-------|--------|------------------------|
| `/rentals/v1/<service>/**` | **Current** — preferred for all new clients | `X-API-Version: v1` |
| `/api/<service>/**`         | **Deprecated** — kept for back-compat for one release cycle | `X-API-Version: legacy`, `X-Deprecation: true`, `X-Deprecation-Hint: Use /rentals/v1/...` |

Both shapes are rewritten to the same internal controller path before being signed and forwarded — the controllers in each service stay unversioned (`/auth/login`, `/properties/buildings`, …). When v2 ships you simply add another route block beside `v1` with a different `RewritePath`/controller mapping; existing v1 clients keep working untouched.

```yaml
# api-gateway/src/main/resources/application.yaml (excerpt)
- id: auth-service-v1
  uri: lb://HRA-auth-service
  predicates: [Path=/rentals/v1/auth/**]
  filters:
    - RewritePath=/rentals/v1/(?<segment>.*), /${segment}
    - AddResponseHeader=X-API-Version, v1
    - name: CircuitBreaker
      args: { name: authServiceCircuitBreaker, fallbackUri: forward:/__cb/fallback/auth }
```

### Load balancing

* `lb://HRA-<service>` — every route resolves through Spring Cloud LoadBalancer, which pulls instance lists from Eureka.
* Round-robin across healthy instances; instance health is sourced from Eureka heartbeats (10s renew / 30s lease).
* The gateway has `spring.cloud.loadbalancer.retry.enabled=true` with `max-retries-on-next-service-instance: 1` for **idempotent verbs only** — POST/PATCH never retry to avoid double-writes.
* The instance list is cached for 30s so we don't pound Eureka on every request.

### Circuit breakers (Resilience4j)

There are **two layers** of breakers:

1. **Gateway → service** (per-route). Each `lb://` route has its own named breaker — `authServiceCircuitBreaker`, `propertyServiceCircuitBreaker`, etc. — so one downstream going dark doesn't open everyone's breaker. When open, the route forwards to `/__cb/fallback/<service>` which returns a tidy 503 envelope with `Retry-After: 10`.
2. **Service → service** (per-Feign-client). `auth-service`'s call into User Service and `user-service`'s calls into Auth + Property are wrapped by `feign.circuitbreaker.enabled=true`. Each Feign client has a `*FallbackFactory` that decides whether to throw 503 (so the surrounding `@Transactional` rolls back, e.g. don't keep an Auth row if user-profile creation fails) or to return an empty/degraded payload (e.g. an empty tenant list for an owner-detail call).

Default config (overridable per instance):

| Setting | Value |
|---------|-------|
| `slidingWindowSize` | 20 calls |
| `minimumNumberOfCalls` | 5 |
| `failureRateThreshold` | 50% |
| `slowCallRateThreshold` | 50% (calls > 5s count as slow) |
| `waitDurationInOpenState` | 10s |
| `permittedNumberOfCallsInHalfOpenState` | 3 |
| TimeLimiter (gateway) | 10s |
| TimeLimiter (Feign)   | 6s |
| Retry (Feign)         | 3 attempts, 500ms initial, exp×2; only on `RetryableException`/`TimeoutException`/`IOException` |

Inspect breaker state any time:

```bash
curl http://localhost:8080/actuator/health        # rolled-up gateway health
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state
curl http://localhost:9090/actuator/health        # auth-service Feign breakers
```

## Swagger / OpenAPI

Every service exposes its OpenAPI 3.0 spec via `springdoc-openapi`, and the gateway aggregates all of them into a single Swagger UI.

### Unified Swagger UI (recommended)

```
http://localhost:8080/swagger-ui.html
```

In the **Definition** dropdown (top-right) you'll see all 7 services:

```
1. Auth Service
2. Property Service
3. User Service
4. Payment Service
5. Maintenance Service
6. Notification Service
7. Analytics Service
```

Pick one to load its endpoints. Click the **Authorize** button (padlock icon), paste your JWT (just the token — don't prefix with `Bearer`), and Swagger UI will attach `Authorization: Bearer <token>` to every Try-It-Out request across every service.

### Per-service Swagger UI (debugging)

If the gateway is down or you want to hit a service directly (and HMAC enforcement allows it), each service still serves its own UI:

| Service | URL |
|---------|-----|
| Auth         | `http://localhost:9090/swagger-ui.html` |
| Property     | `http://localhost:8088/swagger-ui.html` |
| User         | `http://localhost:8089/swagger-ui.html` |
| Payment      | `http://localhost:8084/swagger-ui.html` |
| Maintenance  | `http://localhost:8085/swagger-ui.html` |
| Notification | `http://localhost:8086/swagger-ui.html` |
| Analytics    | `http://localhost:8087/swagger-ui.html` |

### Raw OpenAPI specs (for code-generation)

```
http://localhost:8080/aggregate/auth-service/v3/api-docs
http://localhost:8080/aggregate/property-service/v3/api-docs
http://localhost:8080/aggregate/user-service/v3/api-docs
http://localhost:8080/aggregate/payment-service/v3/api-docs
http://localhost:8080/aggregate/maintenance-service/v3/api-docs
http://localhost:8080/aggregate/notification-service/v3/api-docs
http://localhost:8080/aggregate/analytics-service/v3/api-docs
```

Pipe any of these into `openapi-generator-cli`, `nswag`, or your tool of choice to scaffold a typed client.

### How it works

* The gateway has 7 routes of the form `Path=/aggregate/<service>/v3/api-docs/**` → `lb://HRA-<service>` with `StripPrefix=2`. The path that arrives at each service is the canonical `/v3/api-docs`.
* `springdoc.swagger-ui.urls` in the gateway YAML lists those proxy URLs and labels them for the dropdown.
* `OpenApiConfig` in every service declares a `bearerAuth` HTTP-Bearer/JWT scheme, which is what makes the **Authorize** button appear and apply your token globally.
* The gateway's `app.gateway.public-paths` includes `/aggregate/**`, `/swagger-ui/**`, `/v3/api-docs/**`, and `/webjars/**` so loading the docs doesn't require a JWT of its own.

## Cross-cutting design choices

| Concern | Where it lives |
|---------|---------------|
| **Centralised config** | Config Server serves per-service `.yml` from `config-server/src/main/resources/config/`. Each service only carries `spring.config.import=optional:configserver:...` plus safety baselines. |
| **Service discovery** | Eureka. All services use `lb://HRA-<name>` URIs. |
| **Authentication** | JWT issued by Auth Service, validated at the API Gateway. Gateway adds `X-Auth-User-Name` / `X-Auth-Roles` headers. |
| **Gateway-only access** | Every request leaving the gateway is signed with HMAC-SHA256 over `timestamp:method:path`. `auth-commons` `GatewayAuthFilter` enforces this on every downstream service — direct hits return 403. |
| **Service-to-service calls** | Feign clients with `FeignGatewaySigningInterceptor` — sign outbound calls with the same HMAC so the receiving service's `GatewayAuthFilter` accepts them. |
| **Eventing** | Kafka. All event DTOs + producer beans live in the `KafkaEvents` shared library; topic names from `KafkaTopicProperties`. Consumers in Notification + Analytics + Payment + Maintenance. |
| **Exception handling** | Each service has a `@RestControllerAdvice` with the same `APIErrorResponse` envelope. |
| **Logging** | Structured pattern with `traceId`/`spanId` placeholders ready for Micrometer Tracing. |
| **Observability** | Actuator + Micrometer Prometheus on every service; Swagger UI on every service; `/actuator/health/{liveness,readiness}` probes. |

## Project layout

```
Home-Rental-Application/
├── pom.xml                          # Reactor — builds every module in order
├── docker-compose.yml               # Full-stack one-shot
├── README.md                        # This file
├── IMPLEMENTATION-PLAN.md           # Phased roadmap from the initial review
├── Home-Rental-Microservices-Architecture.md   # Original design doc
├── Home-Rental-Implementation-Review.docx      # Audit report
│
├── auth-commons/                    # Shared library
├── KafkaEvents/                     # Shared library
│
├── Service-Registry/                # Eureka
├── config-server/                   # Spring Cloud Config Server
├── api-gateway/                     # Spring Cloud Gateway
│
├── auth-service/
├── property-service/
├── user-service/
├── payment-service/
├── maintenance-service/
├── notification-service/
├── analytics-service/
│
└── frontend/                        # React 18 + Vite + TS + Tailwind + shadcn SPA
```

## Build everything

```bash
mvn clean install -DskipTests       # skip tests
mvn clean install                   # with tests
mvn test                            # only tests
mvn -pl payment-service -am test    # one module + its deps
```

## Production deployment

### 1. Set up the secrets

Copy the template and fill in real values for every `CHANGE_ME_*`
entry. The `SecretsBootstrapValidator` (in `auth-commons`) refuses to
start ANY service under the `prod` profile when a placeholder leaks
through, so this file IS the deploy-day checklist.

```bash
cp .env.example .env
# edit .env — replace every CHANGE_ME_* with a real value
```

Frontend secrets follow the same pattern:

```bash
cp frontend/.env.example frontend/.env
# set VITE_API_BASE_URL to your real gateway URL
```

Neither `.env` is committed (both are in `.gitignore`).

### 2. Build the images

```bash
docker compose --env-file .env \
               -f docker-compose.yml \
               -f docker-compose.prod.yml build
```

The base `docker-compose.yml` is the **dev** flavour (host ports
exposed, profile=dev). `docker-compose.prod.yml` is an **overlay**
that:

- Activates `SPRING_PROFILES_ACTIVE=prod` everywhere (Hibernate
  `ddl-auto=validate`, Flyway on, JSON logging, `management.env`
  endpoint hidden — see each service's `application-prod.yaml`).
- Drops every internal host-port binding. Only the **frontend
  nginx** is exposed on `:80` — every API call goes via
  `/api/* → gateway` through nginx, not directly to the gateway.
- Reads every secret from the `.env` file via interpolation.
- Replaces the dev bind-mount `./uploads:/uploads` with a named
  Docker volume so document blobs survive `docker compose down`.
- Sets memory limits + `restart: unless-stopped` on every service.

Both files together ARE the production-compose; you don't use the
base file alone in prod.

### 3. Run

```bash
docker compose --env-file .env \
               -f docker-compose.yml \
               -f docker-compose.prod.yml up -d
```

In prod the **infrastructure services** (`oracle-db`, `mongodb`,
`zookeeper`, `kafka`) should be commented out of the base
`docker-compose.yml` — point `DB_URL`, `SPRING_DATA_MONGODB_URI`,
`KAFKA_BOOTSTRAP_SERVERS` at managed services (RDS / MongoDB Atlas
/ AWS MSK) instead. The application services are stateless; only
the data stores need careful operations.

### 4. Smoke-test

```bash
# Each service should respond UP
curl -sf https://api.hearth.app/actuator/health/liveness
curl -sf https://api.hearth.app/actuator/health/readiness

# Check the boot log for SecretsBootstrapValidator green-light
docker compose logs auth-service | grep "SecretsBootstrapValidator"
# Expected: "all sensitive secrets passed — no placeholder leakage detected"

# Confirm the management surface is trimmed
curl -sf -o /dev/null -w "%{http_code}\n" https://api.hearth.app/actuator/env
# Expected: 404
```

### 5. Persistent storage notes

The `uploads/` directory in `document-service` holds property
photos, ID-proof scans, signed deed PDFs, etc. In the dev compose
file it's a bind-mount under the repo root; the **prod overlay
replaces it with a named Docker volume** (`hearth-documents`). For
real production scale you should migrate this to S3-compatible
object storage — see `PRODUCTION_READINESS.md` P1-10.

### 6. What's still left

See `PRODUCTION_READINESS.md` for the full P0/P1/P2 punch list of
infrastructure-layer items that aren't in this repo (TLS
termination, k8s manifests, backups + DR, multi-region, etc.).

## Helper scripts

`scripts/` contains a few convenience entry points:

- `scripts/start-all.sh` / `stop-all.sh` — bring the dev stack
  up/down with the right service ordering (Eureka → config server
  → everything else).
- See `scripts/README.md` for the full list.

## Useful docs

- `Home-Rental-Microservices-Architecture.md` — original architecture
- `Home-Rental-Implementation-Review.docx` — initial audit
- `IMPLEMENTATION-PLAN.md` — the phased plan we executed against
- `PRODUCTION_READINESS.md` — prioritised list of what's done +
  what's still required before going live
- Each service folder has its own `README.md` with endpoint
  catalog and config matrix
