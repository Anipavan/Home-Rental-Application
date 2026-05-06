# Property Service

**Port:** `8088`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · Oracle 23c · Apache Kafka · Eureka client

Manages buildings, flats, occupancy lifecycle, and property images for the Home Rental Application. Publishes Kafka events that drive Notification, Payment, and Analytics services.

## Endpoints

### Buildings (`/properties/buildings`)
| Method | Path                             | Description                                                  |
|--------|----------------------------------|--------------------------------------------------------------|
| GET    | `/properties/buildings`          | Paginated list of active buildings                          |
| POST   | `/properties/buildings/create/building` | Create a building → publishes `property.created`     |
| GET    | `/properties/buildings/{buildId}` | Get building by id                                         |
| GET    | `/properties/buildings/owner/{ownerId}` | Buildings owned by a specific owner                  |
| PUT    | `/properties/buildings/{id}/building` | Update building → publishes `property.updated`        |
| DELETE | `/properties/buildings/{buildId}` | Soft-delete (refused if active flats exist)               |

### Flats (`/properties/flats`)
| Method | Path                                  | Description                                              |
|--------|---------------------------------------|----------------------------------------------------------|
| GET    | `/properties/flats`                   | Paginated list of active flats                          |
| GET    | `/properties/flats/{flatId}`          | Get flat by id                                          |
| POST   | `/properties/flats/create/flat`       | Create a new flat                                       |
| PUT    | `/properties/flats/{flatId}`          | Update flat                                             |
| DELETE | `/properties/flats/{flatId}`          | Soft-delete a flat                                      |
| GET    | `/properties/flats/building/{buildId}`| All flats in a building                                 |
| GET    | `/properties/flats/vacant`            | All currently vacant flats                              |
| POST   | `/properties/flats/{flatId}/vacate`   | Mark vacant → publishes `flat.vacated`                  |
| POST   | `/properties/flats/{flatId}/assign`   | Assign tenant + lease → publishes `flat.occupied`       |

### Property Images (`/properties/buildings/{id}/images`)
| Method | Path                                  | Description                          |
|--------|---------------------------------------|--------------------------------------|
| POST   | `/properties/buildings/{id}/images`   | Upload an image (multipart/form-data) |
| GET    | `/properties/buildings/{id}/images`   | List images for a property            |

### Operational
| Path                          | Description                            |
|-------------------------------|----------------------------------------|
| `/swagger-ui.html`            | Interactive API documentation          |
| `/v3/api-docs`                | OpenAPI 3 JSON spec                    |
| `/actuator/health`            | Liveness + readiness probes            |
| `/actuator/prometheus`        | Prometheus metrics scrape endpoint     |

## Kafka

All Kafka infrastructure (topic names, topic auto-creation, producer beans) lives in the shared **`KafkaEvents`** module. This service simply depends on that jar and component-scans `com.spa.home_rental_application.KafkaEvents`. No Kafka boilerplate is duplicated here.

Topic name is sourced from `app.kafka.property-topic` via `KafkaTopicProperties` (in KafkaEvents).

**Produces** (default topic `property-events`):
- `property.created` — emitted by `BuildingImpul.createBuilding` via `PropertyServiceEvents.sendPropertyCreated`
- `property.updated` — emitted by `BuildingImpul.updateBuilding` via `PropertyServiceEvents.sendPropertyUpdated`
- `flat.occupied`   — emitted by `FlatServiceImpul.assignFlat` via `PropertyServiceEvents.sendFlatOccupied`
- `flat.vacated`    — emitted by `FlatServiceImpul.makeFlatVacate` via `PropertyServiceEvents.sendFlatVacated`

**Consumes:** none (producer-only)

## Configuration

Override via environment variables (see `application.yaml` for full list):

| Var                       | Default                                   |
|---------------------------|-------------------------------------------|
| `DB_URL`                  | `jdbc:oracle:thin:@//localhost:1521/orcl` |
| `DB_USERNAME`             | `siva`                                    |
| `DB_PASSWORD`             | `Pavan@123`                               |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093`                          |
| `EUREKA_URL`              | `http://localhost:8761/eureka`            |
| `PROPERTY_UPLOAD_DIR`     | `uploads`                                 |

## Run

```bash
# Local (requires Oracle, Kafka, Eureka up)
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/property-service:0.0.1 .
docker run -p 8088:8088 \
  -e DB_URL=jdbc:oracle:thin:@//host.docker.internal:1521/orcl \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  -v $(pwd)/uploads:/data/uploads \
  home-rental/property-service:0.0.1
```

## Test

```bash
mvn -B test
```

## Known follow-ups

- Some old DTO files (`BuildingCreateRequest`, `BuildingUpdateRequest`, `BuildingResponse`, `BuildingListResponse`) declare `package ...DTO` but live in `DTO/Request/` or `DTO/Response/` directories. The dead `Mapper/BuildingMapper.java` references them. Either delete that mapper or reorganise those files. They don't affect runtime.
- Switch from `ddl-auto: update` to Liquibase or Flyway before production.
