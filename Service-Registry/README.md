# Service Registry (Eureka Server)

**Port:** `8761`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · Netflix Eureka Server

The service-discovery registry for the Home Rental platform. Every business service registers here on startup; the API Gateway resolves `lb://HRA-<service-name>` URIs against this registry to find live instances.

## Endpoints

| URL | Purpose |
|-----|---------|
| `http://localhost:8761/` | Eureka dashboard UI — see registered apps + their instances |
| `http://localhost:8761/eureka/apps` | XML/JSON dump of the registry (used by clients) |
| `http://localhost:8761/actuator/health` | Liveness + readiness probes |
| `http://localhost:8761/actuator/info` | Build info |
| `http://localhost:8761/actuator/prometheus` | Prometheus metrics |

## What registers here

| Application name (from `spring.application.name`) | Owning module |
|---------------------------------------------------|---------------|
| `HRA-api-gateway`        | api-gateway        |
| `HRA-auth-service`       | auth-service       |
| `HRA-property-service`   | property-service   |
| `HRA-user-service`       | user-service       |
| `HRA-maintenance-service`| maintenance-service|
| `HRA-payment-service`    | payment-service (skeleton)    |
| `HRA-notification-service`| notification-service (skeleton) |
| `HRA-analytics-service`  | analytics-service (skeleton)  |

## Configuration

Override via environment variables (see `application.yaml`):

| Var | Default | Notes |
|-----|---------|-------|
| `SERVER_PORT` | `8761` | |
| `EUREKA_HOSTNAME` | `localhost` | Set to the container/VM hostname in non-local deployments |
| `EUREKA_PEER_URL` | `http://${EUREKA_HOSTNAME}:${SERVER_PORT}/eureka/` | For multi-node Eureka clusters point each node at the others |
| `EUREKA_SELF_PRESERVATION` | `false` | Set to `true` in production so transient network blips don't evict healthy instances |

## Tuning notes

- **Self-preservation** is OFF in this config so dead instances are removed quickly during local dev. In production set `EUREKA_SELF_PRESERVATION=true` so the server doesn't deregister instances during a network partition (a "thundering herd" risk on recovery).
- **Eviction interval** is 5s (down from the 60s default) so dev iteration is fast.
- For **HA** in production, run two or more Eureka instances and point each `EUREKA_PEER_URL` at the others; the server will replicate registrations between peers.

## Run

```bash
# Local
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/service-registry:0.0.1 .
docker run -p 8761:8761 home-rental/service-registry:0.0.1
```

## Boot order in the platform

Start in this order so downstream services can register cleanly:

1. **Service Registry** (this service) — port 8761
2. **Config Server** (optional) — port 8888
3. **API Gateway** — port 8080
4. Domain services (auth, property, user, maintenance, payment, notification, analytics) in any order

After ~30 seconds the dashboard at `http://localhost:8761/` should list every running service with at least one UP instance.
