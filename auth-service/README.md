# Auth Service

**Port:** `9090`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Security 6 · jjwt 0.12.5 · Spring Cloud 2024.0.3 · Oracle 23c · Apache Kafka · Eureka · OpenFeign

Issues JWT access tokens + opaque refresh tokens, owns the forgot/reset-password flow, and forwards new accounts to User Service via Feign. Publishes audit events to Kafka.

## Endpoints

### Public (anonymous)
| Method | Path                       | Description                                                          |
|--------|----------------------------|----------------------------------------------------------------------|
| POST   | `/auth/register`           | Create account → publishes `user.registered`, calls User Service     |
| POST   | `/auth/login`              | Authenticate → returns access JWT + refresh token, fires `user.login`|
| POST   | `/auth/refresh`            | Rotate refresh token → new access JWT + new refresh token            |
| POST   | `/auth/logout`             | Revoke refresh token, fires `user.logout`                            |
| POST   | `/auth/forgot-password`    | Start reset flow, fires `user.password.reset.requested`              |
| POST   | `/auth/reset-password`     | Complete reset (consumes the emailed token)                          |

### Admin-only (requires `ROLE_ADMIN`)
| Method | Path                       | Description                                                          |
|--------|----------------------------|----------------------------------------------------------------------|
| GET    | `/auth/role/{roleName}`    | List users with the given role                                       |
| GET    | `/auth/users/{id}`         | Get an auth user by id                                               |

### Operational
| Path                          | Description                            |
|-------------------------------|----------------------------------------|
| `/swagger-ui.html`            | Interactive API documentation          |
| `/v3/api-docs`                | OpenAPI 3 JSON spec                    |
| `/actuator/health`            | Liveness + readiness probes            |
| `/actuator/prometheus`        | Prometheus metrics                     |

## Security

- Stateless: no HTTP session, every authenticated call carries `Authorization: Bearer <jwt>`.
- Access token TTL: 1 hour (override via `JWT_ACCESS_TTL`).
- Refresh token: opaque 64-char UUID stored in DB, TTL 30 days (override via `JWT_REFRESH_TTL`).
- Refresh-token **rotation**: every `/auth/refresh` revokes the presented token and issues a fresh one — replay attempts after rotation are rejected.
- Passwords hashed with BCrypt (12 rounds default).
- Password complexity: ≥ 8 chars, at least one upper, one lower, one digit.
- Forgot-password endpoint always returns 200 — never reveals whether the email is registered.
- All refresh tokens are revoked when a password reset completes (force re-login on every device).
- `JwtAuthenticationFilter` extracts subject + authorities from the bearer token and seeds the `SecurityContextHolder` so `@PreAuthorize` works on controller methods.

## Kafka

All Kafka infrastructure (topic names, topic auto-creation, producer beans) lives in the shared **`KafkaEvents`** module. Topic comes from `app.kafka.auth-topic` via `KafkaTopicProperties`.

**Produces** (default topic `auth-events`):
- `user.registered` — emitted by `register`
- `user.login`     — emitted by `login`
- `user.logout`    — emitted by `logout`
- `user.password.reset.requested` — emitted by `forgot-password`

**Consumes:** none (producer-only)

## Inter-service calls (Feign)

- `HRA-user-service` → `POST /users/user` — pushes a password-free `UserProfileCreateRequest` so User Service can create the linked profile row. The Auth-side row is rolled back if this Feign call fails.

## Configuration

Override via environment variables (see `application.yaml`):

| Var                       | Default                                   |
|---------------------------|-------------------------------------------|
| `SERVER_PORT`             | `9090`                                    |
| `DB_URL`                  | `jdbc:oracle:thin:@//localhost:1521/orcl` |
| `DB_USERNAME`             | `siva`                                    |
| `DB_PASSWORD`             | `Pavan@123`                               |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093`                          |
| `EUREKA_URL`              | `http://localhost:8761/eureka`            |
| `JWT_SECRET`              | base64-encoded HMAC-SHA256 key (≥ 256 bits) |
| `JWT_ACCESS_TTL`          | `3600`                                    |
| `JWT_REFRESH_TTL`         | `2592000`                                 |
| `JWT_ISSUER`              | `home-rental-auth`                        |
| `PASSWORD_RESET_TTL_MINUTES` | `15`                                   |

## Token lifecycle (sequence)

```
register   → POST /auth/register   → user_details_table row + user.registered event + Feign → User Service
login      → POST /auth/login      → access JWT + refresh row in refresh_tokens + user.login event
refresh    → POST /auth/refresh    → revoke old refresh row, issue new one, mint new access JWT
logout     → POST /auth/logout     → revoke refresh row + user.logout event
forgot-pw  → POST /auth/forgot-password → password_reset_tokens row + user.password.reset.requested event
reset-pw   → POST /auth/reset-password → mark token used, set new password hash, revoke ALL refresh rows for user
janitor    → @Scheduled hourly      → DELETE expired refresh + reset rows
```

## Run

```bash
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/auth-service:0.0.1 .
docker run -p 9090:9090 \
  -e DB_URL=jdbc:oracle:thin:@//host.docker.internal:1521/orcl \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  -e JWT_SECRET=<base64-encoded-256-bit-secret> \
  home-rental/auth-service:0.0.1
```

## Test

```bash
mvn -B test
```

## Notes & follow-ups

- The DB-backed refresh token table is the canonical revocation source. Access JWTs themselves are not revocable (would require a JWT blacklist or rotating signing keys); they're short-lived (1 hour) so this is a deliberate trade-off.
- For prod, move `JWT_SECRET` to HashiCorp Vault or AWS Secrets Manager.
- Consider raising BCrypt strength to 14+ for prod deployments behind a CPU-bound login form.
- Consider rate-limiting `/auth/login` and `/auth/forgot-password` at the API Gateway to thwart credential stuffing and email-enumeration attacks.
