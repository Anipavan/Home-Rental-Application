# Analytics Service

**Port:** `8087`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · Oracle 23c · Apache Kafka · Eureka · Spring Cloud Config client · Apache POI (Excel) · OpenPDF (PDF)

Event-sourced read model for the platform. Subscribes to `payment-events`, `property-events`, and `maintenance-events`; aggregates them into four DB tables (`revenue_summary`, `occupancy_stats`, `payment_trends`, `maintenance_metrics`); exposes read APIs and downloadable Excel/PDF reports.

## Cross-cutting concerns honoured

| Concern | Wiring |
|---------|--------|
| **Configurations** | Pulled from Config Server (`HRA-analytics-service.yml`). Local `application.yaml` only carries `spring.config.import` + safety baselines. |
| **Kafka events** | All consumer DTOs are imported from the **KafkaEvents** shared library. Topic names from `KafkaTopicProperties`. |
| **Gateway-only access** | `auth-commons` auto-config enforces HMAC-signed `X-Internal-Auth-Sig` on every inbound request. Direct hits → `403 GATEWAY_REQUIRED`. |
| **Exception handling** | Central `@RestControllerAdvice` (`GlobalExceptionHandler`) with the same `APIErrorResponse` envelope every other service uses. |
| **Logging** | Structured pattern with traceId/spanId placeholders. |
| **Service discovery** | Registers as `HRA-analytics-service` in Eureka. |
| **Observability** | Actuator + Prometheus + springdoc OpenAPI. |

## Endpoints

### Revenue (`/analytics/revenue`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/owner/{ownerId}` | Per-month revenue rows for an owner |
| GET | `/monthly/{year}` | All months of a given year, all owners |
| GET | `/yearly/{ownerId}/{year}` | Yearly total for one owner |
| GET | `/comparison/{ownerId}` | Month-over-month comparison |

### Occupancy (`/analytics/occupancy`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/building/{buildingId}` | Daily snapshots for a building |
| GET | `/overall` | Aggregate occupancy across every building today |
| GET | `/trend/{buildingId}` | Time series (alias) |

### Payment trends (`/analytics/payments`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/collection-rate/{ownerId}` | On-time vs late ratio |
| GET | `/trends/{ownerId}` | Per-month on-time/late counts and avg delay |

### Maintenance (`/analytics/maintenance`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/by-category` | Resolved count + avg resolution minutes per category |
| GET | `/resolution-time` | Weighted-average resolution time across all categories |

### Export (`/analytics/export`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/revenue/pdf/{ownerId}` | Revenue report as PDF (OpenPDF, A4 landscape) |
| GET | `/revenue/excel/{ownerId}` | Revenue report as `.xlsx` (Apache POI) |

### Operational
| Path | Purpose |
|------|---------|
| `/swagger-ui.html` | Interactive API documentation |
| `/v3/api-docs` | OpenAPI 3 JSON spec |
| `/actuator/health` | Liveness + readiness |
| `/actuator/prometheus` | Prometheus metrics |

## Kafka — events consumed

| Event topic | Event | What it updates |
|-------------|-------|-----------------|
| `payment-events`     | `payment.completed` | `revenue_summary` (++ totalPaid, totalRevenue, paymentCount) AND `payment_trends` (on-time vs late, avg delay) |
| `payment-events`     | `payment.overdue`   | `revenue_summary` (++ totalOverdue) |
| `property-events`    | `flat.occupied`     | `occupancy_stats` (occupiedFlats++, recompute rate) |
| `property-events`    | `flat.vacated`      | `occupancy_stats` (occupiedFlats−−, recompute rate) |
| `maintenance-events` | `maintenance.created` | In-memory cache of requestId→category (so the resolved event can attribute correctly) |
| `maintenance-events` | `maintenance.resolved` | `maintenance_metrics` (++ resolvedCount, += resolutionMinutes) |

Each listener uses a distinct consumer group so the broker delivers each event to it independently of the other consumers (Notification etc.).

## Configuration

Local `application.yaml` only contains `spring.config.import=optional:configserver:...` plus safety baselines. The real config lives in `config-server/src/main/resources/config/HRA-analytics-service.yml`.

| Env var | Default |
|---------|---------|
| `SERVER_PORT` | `8087` |
| `CONFIG_SERVER_URL` | `http://localhost:8888` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Oracle conn string |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093` |
| `EUREKA_URL` | `http://localhost:8761/eureka` |
| `INTERNAL_AUTH_SECRET` | shared HMAC with API Gateway |
| `ANALYTICS_EXPORT_DIR` | `reports` (currently unused — exports are streamed, not persisted) |

## Run

```bash
# Pre-reqs: Eureka + Config Server + Oracle + Kafka all up.
cd analytics-service
mvn -B spring-boot:run
```

Verify:
- `http://localhost:8761/` shows `HRA-analytics-service` registered
- `http://localhost:8087/swagger-ui.html` opens API docs
- After triggering some payments via the platform, hit `GET /analytics/revenue/owner/<ownerId>` to see the aggregated rows
- Excel: `GET /analytics/export/revenue/excel/<ownerId>` downloads `revenue-<ownerId>.xlsx`
- PDF: `GET /analytics/export/revenue/pdf/<ownerId>` downloads `revenue-<ownerId>.pdf`

## Notes & follow-ups

- `payment.overdue` doesn't carry `ownerId` in v1 — currently booked against `tenantId` as a fallback. The producer should add `ownerId` and the listener can be updated in v2.
- `flat.vacated` doesn't carry `buildingId` — currently keyed by `flatId` so each vacate at least decrements *something*. Same v2 fix.
- Maintenance resolution attribution uses an in-memory `requestId → category` cache populated on `maintenance.created`. For multi-instance deployments swap to Redis (or have the producer include `category` on the resolved event).
- All four aggregation tables are upsert-style + idempotent — re-delivery of the same Kafka event won't double-count.
- For high-volume reporting consider denormalising into a star schema and offloading exports to a worker pool so they don't tie up Tomcat threads.
