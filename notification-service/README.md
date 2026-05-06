# Notification Service

**Port:** `8086`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · MongoDB · Apache Kafka · Eureka client · Spring Cloud Config client · spring-mail (SMTP)

Multi-channel (email / SMS / push) notification fan-out for the entire platform. Driven primarily by Kafka events from auth, payment, maintenance and property services. Configuration is pulled from **Config Server** (`HRA-notification-service.yml`); event DTOs come from the **KafkaEvents** shared library; gateway-only access is enforced by **auth-commons**.

## How a notification flows

```
Some service publishes event ──▶ Kafka topic ──▶ <Event>Listener.onMessage()
                                                       │
                                                       ▼
                                          NotificationService.sendFromTemplate(
                                              userId, type, category, vars)
                                                       │
                                                       ▼
                                          PreferenceService.findOrDefault()
                                              ├─ category muted → SKIPPED
                                              └─ channel disabled → SKIPPED
                                                       │
                                                       ▼
                                          TemplateService.render()
                                              ├─ template missing → fallback
                                              └─ render {{var}} placeholders
                                                       │
                                                       ▼
                                          NotificationDispatcher.dispatch()
                                              ├─ EmailChannelAdapter (SMTP)
                                              ├─ SmsChannelAdapter (Twilio stub)
                                              └─ PushChannelAdapter (FCM stub)
                                                       │
                                                       ▼
                                       NotificationLog persisted (MongoDB)
                                              SENT | FAILED | RETRY | SKIPPED

                          RetryScheduler picks up FAILED rows every 5 min
                          and re-dispatches up to app.notification.max-retries.
```

## Endpoints

### Manual sends (`/notifications/send/...`)
| Method | Path | Description |
|--------|------|-------------|
| POST   | `/notifications/send/email` | Force-send an email to a user |
| POST   | `/notifications/send/sms`   | Force-send an SMS |
| POST   | `/notifications/send/push`  | Force-send a push notification |

### History (`/notifications`)
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/notifications` | List all notifications (paginated) |
| GET    | `/notifications/user/{userId}` | All notifications for a user |
| GET    | `/notifications/{id}` | Get notification by id |
| GET    | `/notifications/{id}/status` | Status alias |

### Preferences (`/notifications/preferences/{userId}`)
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/notifications/preferences/{userId}` | Get prefs (defaults if none) |
| PUT    | `/notifications/preferences/{userId}` | Upsert prefs (channels + muted categories + email/phone/device-token) |

### Templates (`/notifications/templates`)
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/notifications/templates` | List templates |
| POST   | `/notifications/templates` | Create template |
| PUT    | `/notifications/templates/{id}` | Update template |
| DELETE | `/notifications/templates/{id}` | Delete template |

### Operational
| Path | Purpose |
|------|---------|
| `/swagger-ui.html` | Interactive API documentation |
| `/v3/api-docs` | OpenAPI 3 JSON spec |
| `/actuator/health` | Liveness + readiness |
| `/actuator/prometheus` | Metrics |

## Kafka — events consumed

All Kafka infrastructure (topic names, JSON serialiser/deserialiser, etc.) lives in the **KafkaEvents** shared library. Each listener uses a **distinct consumer group** so Kafka delivers the same event to each independently:

| Event topic | Consumed events | Effect |
|-------------|-----------------|--------|
| `auth-events` | `user.registered` | Welcome email |
| `auth-events` | `user.password.reset.requested` | Reset-link email |
| `payment-events` | `payment.created` | "New invoice" email |
| `payment-events` | `payment.completed` | Receipt email |
| `payment-events` | `payment.overdue` | Overdue email **+ SMS** (high-priority) |
| `payment-events` | `payment.reminder` | Reminder email |
| `maintenance-events` | `maintenance.created` | "Request received" email to tenant |
| `maintenance-events` | `maintenance.assigned` | Email to tenant + technician |
| `maintenance-events` | `maintenance.resolved` | "Request resolved" email |
| `property-events` | `flat.occupied` | Welcome-to-your-flat email |

**Produces:** none yet — the design lists `notification.sent` / `notification.failed` events but they're left as a follow-up. The `NotificationLog` MongoDB collection is the canonical record of what was sent.

## Configuration

This service uses **Spring Cloud Config Server** for its real config. The local `application.yaml` only carries:
- `spring.application.name`
- `spring.config.import=optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`
- A few baselines (logging, actuator, eureka URL, internal-auth secret) so the service can still start if Config Server is briefly down.

The real config (DB URI, SMTP creds, Twilio/FCM keys, retry policy, `delivery-enabled` toggle) lives in `config-server/src/main/resources/config/HRA-notification-service.yml`.

| Env var | Purpose |
|---------|---------|
| `CONFIG_SERVER_URL` | URL of Config Server (default `http://localhost:8888`) |
| `MONGO_URI` | MongoDB connection (default `mongodb://localhost:27017/HomeRentalDB`) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers |
| `EUREKA_URL` | Eureka |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP creds |
| `NOTIFICATION_DELIVERY` | `true` (real adapters) or `false` (no-op adapters that just log) |
| `NOTIFICATION_FROM_EMAIL` / `NOTIFICATION_FROM_NAME` | "From" header on outbound email |
| `TWILIO_SID` / `TWILIO_TOKEN` / `TWILIO_FROM` | Twilio creds (SMS) |
| `FCM_SERVER_KEY` | Firebase Cloud Messaging server key (push) |
| `INTERNAL_AUTH_SECRET` | HMAC shared with API Gateway |

## Default templates

`TemplateSeeder` runs on startup and inserts reasonable default templates for every `NotificationCategory` + `NotificationType` pair the listeners use. Idempotent — replaces nothing on subsequent runs. Operators tweak them via the admin endpoints.

Placeholder syntax in the body is `{{var}}`, e.g.:

```
Hi {{userName}}, your invoice {{invoiceNumber}} for ₹{{amount}} is due {{dueDate}}.
```

Missing variables render as `[var]` in the output so the gap is visible rather than silently corrupting the message.

## Run

```bash
# 1. Eureka up (port 8761)
# 2. Config Server up (port 8888)
# 3. MongoDB up (port 27017)
# 4. Kafka up (port 9093)

mvn -B spring-boot:run

# Disable real delivery for safe local testing:
NOTIFICATION_DELIVERY=false mvn -B spring-boot:run
```

## Notes

- The Email adapter uses `JavaMailSender` (SMTP). For high-volume prod, swap for SendGrid / AWS SES with their async APIs.
- SMS / Push adapters are stubs; both have a clearly-marked `// STUB:` comment showing the one-call swap to the real SDK.
- The `RetryScheduler` is a fixed-delay job. For multi-instance deployments, add ShedLock (or move to Quartz with a shared DB store) so two instances don't double-deliver.
- The `NotificationLog.metadata` map carries the original event variables so failed notifications can be inspected/retried with full context.
