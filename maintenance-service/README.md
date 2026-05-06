# Maintenance Service

**Port:** `8085`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · MongoDB · Apache Kafka · Eureka client

Owns tenant-raised maintenance/repair requests: lifecycle, assignment, comments, status transitions, image uploads, and analytics. Consumes `flat.vacated` from Property Service to auto-close pending requests.

## Endpoints

### Request lifecycle (`/maintenance/requests`)
| Method | Path | Description |
|--------|------|-------------|
| POST   | `/requests` | Create → publishes `maintenance.created` |
| GET    | `/requests` | List (paginated) |
| GET    | `/requests/{id}` | Get by id |
| PUT    | `/requests/{id}` | Update mutable attributes |
| DELETE | `/requests/{id}` | Hard-delete (returns 204) |
| GET    | `/requests/status/{status}` | Filter by status (OPEN/IN_PROGRESS/RESOLVED/CLOSED) |
| GET    | `/requests/priority/{priority}` | Filter by priority (LOW/MEDIUM/HIGH/CRITICAL) |
| GET    | `/requests/category/{category}` | Filter by category (PLUMBING/ELECTRICAL/...) |
| GET    | `/requests/tenant/{tenantId}` | All requests for a tenant |
| GET    | `/requests/owner/{ownerId}` | All requests across an owner's properties |

### Actions (`/maintenance/requests/{id}/...`)
| Method | Path | Description |
|--------|------|-------------|
| POST   | `/{id}/assign` | Assign technician → publishes `maintenance.assigned`. Auto-transitions OPEN → IN_PROGRESS |
| POST   | `/{id}/comment` | Add a comment → publishes `maintenance.comment.added` |
| POST   | `/{id}/status` | Change status (validated via state machine) → publishes `maintenance.status.changed` (and `maintenance.resolved` if to=RESOLVED) |
| POST   | `/{id}/images` | Upload image (multipart, ≤ 5 MB, jpeg/png/webp/gif) |
| GET    | `/{id}/history` | Status-change history |

### Analytics (`/maintenance/stats`)
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/stats/pending` | `{ "pendingCount": N }` (OPEN + IN_PROGRESS) |
| GET    | `/stats/category` | Count grouped by category |
| GET    | `/stats/resolution-time` | Avg / min / max minutes (createdAt → resolvedAt) |

### Operational
| Path | Description |
|------|-------------|
| `/swagger-ui.html` | Interactive API documentation |
| `/v3/api-docs` | OpenAPI 3 JSON spec |
| `/actuator/health` | Liveness + readiness probes |
| `/actuator/prometheus` | Prometheus metrics |

## State machine

```
OPEN ──assign──▶ IN_PROGRESS ──resolve──▶ RESOLVED ──close──▶ CLOSED
 │                  │                       │
 │                  └─revert─▶ OPEN          └─dispute─▶ IN_PROGRESS
 └──vacate-flat────────────────────────────▶ CLOSED
```
Illegal transitions return `409 ILLEGAL_STATUS_TRANSITION`.

## Kafka

All Kafka infrastructure (topic names, topic auto-creation, producer beans) lives in the shared **`KafkaEvents`** module. Topic comes from `app.kafka.maintenance-topic` via `KafkaTopicProperties`.

**Produces** (default topic `maintenance-events`):
- `maintenance.created` — `RequestService.createRequest`
- `maintenance.assigned` — `RequestService.assignTechnician`
- `maintenance.status.changed` — `RequestService.changeStatus` and `onFlatVacated` (auto-close)
- `maintenance.resolved` — `RequestService.changeStatus` when transitioning to RESOLVED (with resolution-time minutes)
- `maintenance.comment.added` — `RequestService.addComment`

**Consumes** (`property-events` topic):
- `flat.vacated` → auto-closes every active request (OPEN/IN_PROGRESS/RESOLVED) for the vacated flat

## Configuration

| Var | Default |
|-----|---------|
| `SERVER_PORT` | `8085` |
| `MONGO_URI` | `mongodb://localhost:27017/HomeRentalDB` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093` |
| `EUREKA_URL` | `http://localhost:8761/eureka` |
| `MAINTENANCE_UPLOAD_DIR` | `uploads/maintenance` |

## Run

```bash
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/maintenance-service:0.0.1 .
docker run -p 8085:8085 \
  -e MONGO_URI=mongodb://host.docker.internal:27017/HomeRentalDB \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  -v $(pwd)/uploads:/data/uploads \
  home-rental/maintenance-service:0.0.1
```

## Notes

- `requestNumber` is generated as `MR-YYMMDD-XXXX` and indexed for uniqueness.
- All Kafka publishes happen inside the same service-method invocation as the DB write — for production, consider an outbox pattern so the two cannot diverge.
- Image upload is to local disk; for prod, swap `PropertyImageService`-style local storage for S3 (or equivalent).
