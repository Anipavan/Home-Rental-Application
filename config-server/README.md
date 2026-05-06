# Config Server

**Port:** `8888`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · Spring Cloud Config Server · Eureka client

Centralised configuration store for the Home Rental platform. Every service can fetch its config here on startup, so secrets/topic names/db URLs live in one place per environment.

## How it works

Active profile is **`native`** by default — Config Server reads `.yml` files from `classpath:/config/` (i.e. `src/main/resources/config/`). Switch to a Git-backed repo for prod by setting `SPRING_PROFILES_ACTIVE=git` and `CONFIG_GIT_URI=https://github.com/your-org/home-rental-config.git`.

## Endpoints (consumed by client services)

```
GET http://localhost:8888/{application}/{profile}
GET http://localhost:8888/{application}/{profile}/{label}

Examples:
  GET http://localhost:8888/HRA-property-service/default
  GET http://localhost:8888/HRA-auth-service/dev
  GET http://localhost:8888/application/default
```

The `application.yml` file in `src/main/resources/config/` is shared by every service. Per-service files (`HRA-auth-service.yml`, `HRA-property-service.yml`, etc.) override the shared values.

## Configs currently served

| File | Served when client requests |
|------|------------------------------|
| `application.yml` | every client (shared baseline) |
| `HRA-auth-service.yml` | `spring.application.name = HRA-auth-service` |
| `HRA-property-service.yml` | `HRA-property-service` |
| `HRA-user-service.yml` | `HRA-user-service` |
| `HRA-maintenance-service.yml` | `HRA-maintenance-service` |
| `HRA-api-gateway.yml` | `HRA-api-gateway` |

Add `HRA-payment-service.yml`, `HRA-notification-service.yml`, `HRA-analytics-service.yml` as those services come online.

## How a client service uses this

In each client service's `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

In each client service's `src/main/resources/application.yml` (or `bootstrap.yml`):

```yaml
spring:
  application:
    name: HRA-property-service        # MUST match the file name above
  config:
    import: optional:configserver:http://localhost:8888
```

(Use `optional:` so the service still starts during local dev if Config Server is down.)

For Eureka-based discovery instead of a hardcoded URL:

```yaml
spring:
  config:
    import: optional:configserver:
  cloud:
    config:
      discovery:
        enabled: true
        service-id: HRA-config-server
```

## Operational endpoints

| Path | Purpose |
|------|---------|
| `http://localhost:8888/actuator/health` | Liveness + readiness |
| `http://localhost:8888/actuator/refresh` | Tell THIS server to re-scan `classpath:/config/` (POST) |
| `http://localhost:8888/actuator/prometheus` | Metrics scrape |

For client-side refresh use `@RefreshScope` on the bean and POST to `/actuator/refresh` on the **client**.

## Configuration

| Env var | Default | Notes |
|---------|---------|-------|
| `SERVER_PORT` | `8888` | |
| `SPRING_PROFILES_ACTIVE` | `native` | Set to `git` to switch to a Git-backed config repo |
| `CONFIG_SEARCH_LOCATIONS` | `classpath:/config/` | Native-mode source dir |
| `CONFIG_GIT_URI` | placeholder | Used only when profile is `git` |
| `CONFIG_GIT_BRANCH` | `main` | |
| `EUREKA_URL` | `http://localhost:8761/eureka` | |
| `EUREKA_REGISTER` | `true` | |

## Run

```bash
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/config-server:0.0.1 .
docker run -p 8888:8888 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  home-rental/config-server:0.0.1
```

Quick verification once it's up:

```bash
curl http://localhost:8888/HRA-property-service/default
```

You should see a JSON response containing the `propertySources` array with the merged config from `application.yml` + `HRA-property-service.yml`.

## Notes

- **Boot order**: start Eureka first (port 8761), then Config Server, then any client. Clients can survive a Config Server outage if they have `optional:` on the import; otherwise they fail to start.
- **Sensitive values**: even with Config Server, secrets should still come from env vars (or HashiCorp Vault, AWS Secrets Manager, etc.). The yml files reference them via `${VAR:default}` so prod can override without changing files.
- **Hot reload**: when you change a yml under `classpath:/config/`, hit `POST /actuator/refresh` on each affected client (not on Config Server). Better: wire up Spring Cloud Bus + Kafka so a refresh on one node fans out automatically.
