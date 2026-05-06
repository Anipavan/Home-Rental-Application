# User Service

**Port:** `8089`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · Oracle 23c · Apache Kafka · Eureka client · OpenFeign

Owns user-profile, owner-profile, and emergency-contact data. Joins to Auth Service (for roles) and Property Service (for tenant lookup) via Feign clients.

## Endpoints

### Users (`/users`)
| Method | Path                          | Description                                          |
|--------|-------------------------------|------------------------------------------------------|
| POST   | `/users/user`                 | Create user → publishes `user.profile.created`       |
| GET    | `/users`                      | List active users (paginated)                        |
| GET    | `/users/user/{userId}`        | Get user by id                                       |
| GET    | `/users/email/{email}`        | Get user by email                                    |
| GET    | `/users/auth/{authUserId}`    | Get user by Auth-Service id                          |
| PUT    | `/users/user/{id}`            | Update user → publishes `user.profile.updated` (diff)|
| DELETE | `/users/{id}`                 | Soft-delete user                                     |
| GET    | `/users/search/{searchParam}` | Search by phone, email or first-name fragment        |
| PUT    | `/users/{userId}/documents`   | Upload PROFILE or ID_PROOF document                  |
| GET    | `/users/role/{roleName}`      | List users by role (joins via Auth Service Feign)    |

### Owners (`/users/owners`)
| Method | Path                                  | Description                                          |
|--------|---------------------------------------|------------------------------------------------------|
| POST   | `/users/owners`                       | Create owner → publishes `owner.registered`          |
| GET    | `/users/owners`                       | List owners (paginated)                              |
| GET    | `/users/owners/{ownerId}`             | Get owner by id                                      |
| GET    | `/users/owners/{ownerId}/tenants`     | List tenants for owner (joins via Property Feign)    |
| PUT    | `/users/owners/{ownerId}`             | Update owner profile                                 |

### Emergency Contacts (`/users/contacts`)
| Method | Path                                  | Description                          |
|--------|---------------------------------------|--------------------------------------|
| POST   | `/users/contacts`                     | Create contact (validates user)      |
| GET    | `/users/contacts`                     | List all (paginated)                 |
| GET    | `/users/contacts/user/{userId}`       | List contacts for a user             |
| PUT    | `/users/contacts/{contactId}`         | Update a contact                     |
| DELETE | `/users/contacts/{contactId}`         | Delete a contact                     |

### Operational
| Path                          | Description                            |
|-------------------------------|----------------------------------------|
| `/swagger-ui.html`            | Interactive API documentation          |
| `/v3/api-docs`                | OpenAPI 3 JSON spec                    |
| `/actuator/health`            | Liveness + readiness probes            |
| `/actuator/prometheus`        | Prometheus metrics scrape endpoint     |

## Kafka

All Kafka infrastructure (topic names, topic auto-creation, producer beans) lives in the shared **`KafkaEvents`** module. Topic name comes from `app.kafka.user-topic` via `KafkaTopicProperties`.

**Produces** (default topic `user-events`):
- `user.profile.created` — emitted by `UserServiceImpul.createUser`
- `user.profile.updated` — emitted by `UserServiceImpul.updateUser` when at least one field changes (event payload includes a human-readable diff)
- `owner.registered`     — emitted by `OwnerServiceImpul.createOwner`

**Consumes:** none yet (the design specifies a `user.registered` listener from Auth — added in the Auth Service iteration when that producer is wired)

## Inter-service calls (Feign)

- `auth-service` → `GET /auth/role/{roleName}` (for role-based user listing)
- `HRA-property-service` → `GET /properties/buildings/owner/{ownerId}/tenants` (for owner→tenants lookup)

## Configuration

Override via environment variables (see `application.yaml`):

| Var                       | Default                                   |
|---------------------------|-------------------------------------------|
| `DB_URL`                  | `jdbc:oracle:thin:@//localhost:1521/orcl` |
| `DB_USERNAME`             | `siva`                                    |
| `DB_PASSWORD`             | `Pavan@123`                               |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093`                          |
| `EUREKA_URL`              | `http://localhost:8761/eureka`            |
| `USER_UPLOAD_DIR`         | `uploads/users`                           |

## Run

```bash
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/user-service:0.0.1 .
docker run -p 8089:8089 \
  -e DB_URL=jdbc:oracle:thin:@//host.docker.internal:1521/orcl \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  -v $(pwd)/uploads:/data/uploads \
  home-rental/user-service:0.0.1
```

## Test

```bash
mvn -B test
```

## Notes

- Soft delete only — `User.isDeleted=true` and `deletedAt=now()`. All queries use `findActiveById` / `findAllActive` so soft-deleted users are invisible.
- Email uniqueness is enforced at both the entity (`unique = true`) and service layer (`existsByEmailIgnoreCaseAndIsDeletedFalse` pre-check) so we get a clean `409 DUPLICATE_USER` instead of an opaque DB constraint error.
- Document uploads validated for type (`PROFILE` or `ID_PROOF`), content-type (jpeg/png/webp/pdf) and size (≤ 5 MB).
- Owner→tenants lookup is delegated to Property Service via Feign — flat→tenant linkage is owned there, not duplicated here.
