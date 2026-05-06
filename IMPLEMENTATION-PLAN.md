# Home Rental Application — Production Hardening Plan

**Approach:** Service-by-service. Most-complete services first. Pause for approval after each.
**Repository:** `C:\Siva\Microservices\Home-Rental-Application`
**Standard stack:** Spring Boot 3.4.5, Spring Cloud 2024.0.2, Java 21, Oracle (existing), Kafka, Eureka.

---

## Order of Execution

| # | Service | Current % | Effort | Why this order |
|---|---------|-----------|--------|----------------|
| 1 | **Property Service** | ~75% | Low | Most complete; ship fast wins, validate the pattern. |
| 2 | **User Service** | ~70% | Low | Same template applied. |
| 3 | **Auth Service** | ~55% | Medium | Critical path; needs refresh/logout/reset/RBAC. |
| 4 | **Maintenance Service** | ~50% | Medium | MongoDB, finish workflow + Kafka. |
| 5 | **KafkaEvents lib** | ~65% | Low | Add Auth/Payment/Notif events; consumer adapters. |
| 6 | **API Gateway** | ~15% | Medium | Routes + JWT filter binding + rate limit + CORS. |
| 7 | **Eureka Server** | ~10% | Low | One config file. |
| 8 | **Config Server** | ~10% | Low | Native backend with config-repo. |
| 9 | **Payment Service** | 5% | High | Greenfield: schema, gateway, invoices, Kafka. |
| 10 | **Notification Service** | 5% | High | Greenfield: 8 Kafka consumers, email/SMS/push. |
| 11 | **Analytics Service** | 5% | High | Greenfield: aggregation, reports, exports. |

KYC/Lease/Review/Compliance/Documentation are **out of scope** until designed-in.

---

## Per-Service Definition of Done (production-ready)

Each service must have:

1. **Configuration** — `application.yaml` with port, datasource, Kafka, Eureka, actuator, logging.
2. **Build** — pom.xml with Actuator, Validation, OpenAPI/springdoc, micrometer-prometheus, devtools optional.
3. **Bootstrap** — `@EnableEurekaClient` (auto), `@EnableKafka` (where producing/consuming), `@EnableJpaAuditing` (where applicable).
4. **API surface** — every endpoint in the design, valid request/response DTOs, validation annotations, OpenAPI docs at `/swagger-ui.html`.
5. **Persistence** — entities with proper column types, constraints, audit fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`).
6. **Event publishing** — every Kafka producer call wired into the corresponding write path.
7. **Event consumption** — `@KafkaListener` methods for all events the design says this service consumes.
8. **Error handling** — global `@RestControllerAdvice` with consistent `ApiErrorResponse`; handles `MethodArgumentNotValidException`, `ConstraintViolationException`, `RecordNotFoundException`, generic `Exception`.
9. **Observability** — `/actuator/health`, `/actuator/info`, `/actuator/prometheus`; structured JSON logs.
10. **Security** — JWT validation when called via Gateway; `@PreAuthorize` on role-restricted endpoints.
11. **Tests** — at least one happy-path slice test per controller (`@WebMvcTest`) + service unit test.
12. **README** — purpose, port, endpoints, env vars, run/test commands.
13. **Dockerfile** — multi-stage JVM image.

---

## Service 1 — Property Service: Concrete Tasks

### Bugs to fix
- `FlatController.makeFlatVacate`: path is `/flats/{id}/vacate` but variable is `flatId` — won't bind. Fix.
- `FlatController.updateFlat`: missing `@PathVariable` annotation on `flatId`. Fix.
- `FlatController.assignFlat`: takes only `userId`; should take `flatId` + tenant info + lease dates and emit `flat.occupied`.
- `BuildingImpul.deleteBuildingById`: validates against `buildingTotalFlats` string emptiness — won't catch real flats. Use `flatRepo.findByBuildingId(buildId).isEmpty()`.
- `PropertyController` uses `/buildings/{id}/images` but other controllers use `/properties/...` — align under `/properties/buildings/{id}/images`.
- `Building.buildingTotalFloors` and `buildingTotalFlats` are `String` — should be `Integer`.

### Additions
- `springdoc-openapi-starter-webmvc-ui` dependency + `OpenApiConfig`.
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`.
- `flat.occupied` Kafka publish on `assignFlat`.
- `MethodArgumentNotValidException` handler in `ExceptionClass`.
- Validation annotations on `BuildingRequestDTO` and `FlatRequestDTO`.
- `@ConfigurationProperties("app.kafka")` for topic names.
- Kafka topic auto-create beans (`NewTopic`).
- README + Dockerfile.

### Out of scope here
- Migrations to Liquibase/Flyway (left as future work — `ddl-auto: update` retained for now).
- Switching DB to PostgreSQL (separate decision).

---

## Hand-off Protocol

After each service:
1. Build it (`mvn -pl <service> -am compile` mentally — I won't actually run mvn from this tool).
2. Summarize what changed, list every new/edited file, flag any TODOs left for you.
3. Wait for **"proceed to next"** before starting the next service.

---

*Plan finalised 29 April 2026. Implementation begins with Property Service.*
