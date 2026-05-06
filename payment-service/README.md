# Payment Service

**Port:** `8084`
**Tech:** Spring Boot 3.4.5 · Java 21 · Spring Cloud 2024.0.2 · Oracle 23c · Apache Kafka · Eureka client

Owns the rent invoice → payment → receipt lifecycle. Talks to a pluggable payment-gateway abstraction that supports **UPI** (with named UPI apps: GPay, PhonePe, Paytm, BHIM, AmazonPay, CRED, WhatsApp, Other), **cards** (Visa/Mastercard/RuPay/Amex/Diners/Discover), **net banking**, **wallets** (Paytm/AmazonPay/PhonePe/MobiKwik/FreeCharge/JioMoney/OlaMoney), **bank transfer**, and **cash**. Active gateway is selected by `app.payment.gateway` (`mock` | `razorpay` | `stripe`).

## Endpoints

### Lifecycle (`/payments`)
| Method | Path                            | Description |
|--------|---------------------------------|-------------|
| POST   | `/payments`                     | Create invoice manually → publishes `payment.created` |
| GET    | `/payments`                     | List paginated |
| GET    | `/payments/{id}`                | By id |
| GET    | `/payments/tenant/{tenantId}`   | All for a tenant |
| GET    | `/payments/owner/{ownerId}`     | All for an owner |
| GET    | `/payments/overdue`             | All currently OVERDUE |
| POST   | `/payments/{id}/pay-cash`       | Owner records a cash payment manually |
| GET    | `/payments/{id}/invoice`        | Get invoice for the payment |
| GET    | `/payments/{id}/receipt`        | Get receipt (only after PAID) |

### Gateway flow (`/payments`)
| Method | Path | Description |
|--------|------|-------------|
| POST   | `/payments/initiate` | Tenant chooses method (UPI/CARD/...) → returns redirect URL / UPI intent / bank details |
| POST   | `/payments/verify`   | Confirm gateway transaction → marks PAID, fires `payment.completed` |
| POST   | `/payments/webhook`  | Async gateway push (Razorpay/Stripe webhook signature verified) |
| GET    | `/payments/mock/return` | Dev only — return URL the MockPaymentGateway redirects to |

### Analytics (`/payments/stats`)
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/payments/stats/tenant/{tenantId}` | Counts + sums for a tenant |
| GET    | `/payments/stats/owner/{ownerId}`   | Counts + sums for an owner |
| GET    | `/payments/history/tenant/{tenantId}` | Tenant payment history (alias) |

### Operational
| Path | Purpose |
|------|---------|
| `/swagger-ui.html` | Interactive API documentation |
| `/v3/api-docs` | OpenAPI 3 JSON spec |
| `/actuator/health` | Liveness + readiness |
| `/actuator/prometheus` | Metrics |

## Payment-method matrix

When initiating a payment via `POST /payments/initiate`, the request body's `paymentMethod` chooses the flow:

| `paymentMethod` | Required extra fields | Initiate response includes |
|-----------------|-----------------------|-----------------------------|
| `UPI` | `upiApp` (recommended); `upiVpa` if collect-style flow | `upiIntentUrl` (mobile deep-link) and/or `upiCollectStatus` |
| `CARD` | `cardNetwork` and `cardLast4` (optional, for receipt only) | `redirectUrl` to the gateway's hosted card form |
| `NET_BANKING` | none | `redirectUrl` to bank selection |
| `WALLET` | `walletProvider` | `redirectUrl` to the wallet's auth flow |
| `BANK_TRANSFER` | none | `bankAccountNumber`, `bankIfsc`, `bankAccountName` to transfer to |
| `CASH` | use `/payments/{id}/pay-cash` instead | n/a |

Full PAN never leaves the client device — the gateway hosts the card form. We only persist `cardNetwork` + `cardLast4` for the receipt.

## Kafka

All Kafka infrastructure lives in the **`KafkaEvents`** shared library. Topic from `app.kafka.payment-topic` via `KafkaTopicProperties`.

**Produces** (default topic `payment-events`):
- `payment.created` — `createPayment` and the `flat.occupied` consumer
- `payment.completed` — `verifyPayment` (gateway) and `payCash` (manual)
- `payment.failed` — `verifyPayment` when gateway returns failure
- `payment.overdue` — daily overdue sweep at 02:00
- `payment.reminder` — daily reminder sweep at 09:00 for payments due in N days

**Consumes** (`property-events` topic):
- `flat.occupied` → seed first invoice for the new tenant
- `flat.vacated` → cancel all active payments for the flat

## Scheduled jobs (`@EnableScheduling`)

| Cron | Job | What it does |
|------|-----|--------------|
| `0 0 2 * * *` | Overdue sweep | Marks PENDING payments past due date as OVERDUE, accrues late fee, fires `payment.overdue` |
| `0 0 9 * * *` | Reminder sweep | Fires `payment.reminder` for payments due in `app.payment.reminder-days-before-due` days |

Late-fee accrual: `lateFeePercentPerWeek` × ceil(daysOverdue ÷ 7), capped at `maxLateFeePercent` of base amount.

## Configuration

| Env var | Default |
|---------|---------|
| `SERVER_PORT` | `8084` |
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1521/orcl` |
| `DB_USERNAME` | `siva` |
| `DB_PASSWORD` | `Pavan@123` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093` |
| `EUREKA_URL` | `http://localhost:8761/eureka` |
| `INTERNAL_AUTH_SECRET` | (shared HMAC with API Gateway) |
| `PAYMENT_GATEWAY` | `mock` (also: `razorpay`) |
| `LATE_FEE_PCT` | `2.0` |
| `MAX_LATE_FEE_PCT` | `10.0` |
| `REMINDER_DAYS` | `3` |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` | placeholders, override in prod |

## Run

```bash
mvn -B spring-boot:run

# Docker
mvn -B -DskipTests package
docker build -t home-rental/payment-service:0.0.1 .
docker run -p 8084:8084 \
  -e DB_URL=jdbc:oracle:thin:@//host.docker.internal:1521/orcl \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  -e PAYMENT_GATEWAY=mock \
  home-rental/payment-service:0.0.1
```

## Adding a new payment processor

1. Implement `PaymentGateway` (see `MockPaymentGateway` and `RazorpayPaymentGateway` for the shape).
2. Register a `@ConditionalOnProperty` bean in `PaymentGatewayConfig`.
3. Set `PAYMENT_GATEWAY=<name>` in env.

Done. No other code changes needed.

## Notes & follow-ups

- The Razorpay implementation contains real signature-verification logic but the `initiate()` step is a stub (returns a fabricated `order_id`). Drop in `com.razorpay:razorpay-java` and replace with the real `Orders.create()` call.
- For full PCI compliance: keep card collection on the gateway's hosted form (`redirectUrl` flow). This service never touches a full PAN.
- Webhook handler currently logs the verified webhook but doesn't auto-mark the payment paid — production deployment should parse the gateway-specific JSON body and call `markPaid()`.
- For prod, switch from local `@Scheduled` jobs to a distributed scheduler (Quartz with DB store, or ShedLock) so multiple instances don't double-process the overdue/reminder sweeps.
