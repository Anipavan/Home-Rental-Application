# API Gateway

**Port:** `8080`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud Gateway (Netty) · Spring Cloud 2024.0.2 · Resilience4j · jjwt 0.12.5 · Eureka client

The single ingress point for the platform. Validates JWTs, blocks direct hits on downstream services with HMAC-signed requests, applies CORS, circuit-breaks failing services, and routes via Eureka.

## Routing

| External path                | Routes to                       | Strip prefix |
|------------------------------|---------------------------------|--------------|
| `/api/auth/**`               | `lb://HRA-auth-service`         | yes |
| `/api/properties/**`         | `lb://HRA-property-service`     | yes |
| `/api/users/**`              | `lb://HRA-user-service`         | yes |
| `/api/payments/**`           | `lb://HRA-payment-service`      | yes |
| `/api/maintenance/**`        | `lb://HRA-maintenance-service`  | yes |
| `/api/notifications/**`      | `lb://HRA-notification-service` | yes |
| `/api/analytics/**`          | `lb://HRA-analytics-service`    | yes |

Strip-prefix=1 means client → `/api/properties/buildings`  becomes  property-service ← `/properties/buildings`.

## Authentication flow

```
Client                Gateway                 Auth Service              Other downstream service
  │                      │                         │                              │
  │── login ────────────▶│── /auth/login ─────────▶│                              │
  │                      │                  validates user                        │
  │                      │                         │                              │
  │      access JWT      │                         │                              │
  │      refresh token   │                         │                              │
  │◀─────────────────────│◀── 200 + AuthResponse ──│                              │
  │                      │                         │                              │
  │── GET /api/properties/buildings (Authorization: Bearer <jwt>)                 │
  │                      │                                                        │
  │     1. JWT filter validates the bearer token                                   │
  │     2. Stamps X-Auth-User-Name / X-Auth-User-Id / X-Auth-Roles                 │
  │     3. Signing filter adds HMAC X-Internal-Auth-Sig + Ts                       │
  │                      │── /properties/buildings ─────────────────────────────▶│
  │                      │                                                        │ ▲
  │                      │                              GatewayAuthFilter:        │ │
  │                      │                              - verifies HMAC sig       │ │
  │                      │                              - timestamp ±60s          │ │
  │                      │                              - reads X-Auth-* headers  │ │
  │                      │                              - sets Authentication     │ │
  │                      │◀──────────── 200 OK + body ────────────────────────────│
  │◀─────── 200 ─────────│
```

Direct hit (no gateway):

```
Attacker ── GET http://property-service:8088/properties/buildings ──▶ property-service
                                                                        │
                                                                        ▼
                                                  GatewayAuthFilter sees no signature
                                                          │
                                                          ▼
                                              ◀── 403 GATEWAY_REQUIRED ─────
```

## Token-expiry / refresh handling

When the access JWT is expired, the gateway responds:

```http
HTTP/1.1 401 Unauthorized
X-Token-Expired: true
Content-Type: application/json

{
  "timestamp": "...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Access token has expired. Call POST /api/auth/refresh with your refresh token to obtain a new one.",
  "errorCode": "TOKEN_EXPIRED",
  "path": "/api/properties/buildings"
}
```

The client recognises `X-Token-Expired: true`, calls `POST /api/auth/refresh` with its refresh token, gets a fresh access JWT + refresh token, and retries the original call.

For an **invalid** (not just expired) token the gateway sends `X-Token-Invalid: true` instead — the client must re-login.

For a **missing** token the gateway sends `X-Auth-Required: true`.

## Public paths (no JWT required)

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/refresh`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `/actuator/**`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `/__cb/**` (circuit-breaker fallbacks)

These STILL get the gateway HMAC signature attached, so downstream services accept them — but the JWT filter doesn't run on them.

## Defence-in-depth: header sanitisation

The gateway scrubs any client-supplied `X-Auth-*` headers from the inbound request before it sets its own. Even on public endpoints. So a client cannot forge identity by pre-setting `X-Auth-User-Name: admin`.

## Configuration

| Var | Default |
|-----|---------|
| `SERVER_PORT` | `8080` |
| `JWT_SECRET` | base64 256-bit secret (must match Auth Service) |
| `JWT_ISSUER` | `home-rental-auth` |
| `INTERNAL_AUTH_SECRET` | base64 ≥128-bit secret (must match every downstream service) |
| `EUREKA_URL` | `http://localhost:8761/eureka` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

## Run

```bash
mvn -B spring-boot:run

# Docker (build local image)
mvn -B -DskipTests package
docker build -t home-rental/api-gateway:0.0.1 .
```

## Notes & follow-ups

- Rate limiting is wired but commented out because Spring Cloud Gateway's built-in `RequestRateLimiter` requires Redis. Deploy a Redis instance and uncomment the `default-filters` block in `application.yaml`.
- For replay protection beyond ±60s, a future iteration could add a per-request nonce stored in Redis with a short TTL.
- The gateway issues no JWT itself — that's Auth Service's job. The gateway only **validates** them.
- Consider mTLS between the gateway and downstream services if your network is not already trusted. The HMAC layer protects against simple direct-access attacks but not full network compromise.
