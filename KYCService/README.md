# KYC Service (port 8092)

Aadhaar / PAN / DigiLocker verification for the RentGenius platform.

## Endpoints (mounted under `/kyc`)

| Method | Path                       | Purpose                                          |
| ------ | -------------------------- | ------------------------------------------------ |
| POST   | `/kyc/initiate/{userId}`   | Start an Aadhaar / DigiLocker KYC flow           |
| GET    | `/kyc/status/{userId}`     | Current KYC status for a user                    |
| POST   | `/kyc/verify-pan`          | Verify a PAN number → publishes `kyc.pan.verified` |
| POST   | `/kyc/digilocker/link`     | Mark DigiLocker linked after consent             |
| GET    | `/kyc/report/{userId}`     | Compliance-grade report (owner dashboard)        |
| POST   | `/kyc/webhook/digio`       | Provider callback (success / failure)            |

API Gateway exposes these under `/api/kyc/**` (StripPrefix=1).

## Kafka

* **Topic published:** `kyc-events`
* **Events emitted:**
    * `kyc.verified` — webhook flagged success
    * `kyc.failed`   — webhook flagged failure
    * `kyc.pan.verified` — PAN-only verification success
* **Topic consumed:** `user-events` (`user.profile.created` → seeds PENDING stub)

## Providers

`app.kyc.provider` (default `MOCK`):

* `MOCK`  – in-process stub for dev / CI; always succeeds.
* `DIGIO` – calls Digio REST API; protected by Resilience4j circuit breaker
  (instance `digio-client`) and `@Retryable` with exponential backoff.

## DPDP Act 2023 compliance

* Aadhaar is **never** stored in plain text — only `SHA-256(salt || aadhaar)`
  in `kyc_records.aadhaar_number_hash`. The salt comes from
  `app.kyc.aadhaar-hash-salt` (per environment).
* Explicit consent text is mandatory on `/kyc/initiate` and recorded in
  `consent_recorded`.
* PAN is stored encrypted at rest (DB-level TDE in production) and never
  fully echoed in API responses (`panMasked` only).

## Local run

```bash
mvn -pl KafkaEvents,auth-commons,KYCService -am spring-boot:run
```

Swagger UI: <http://localhost:8092/swagger-ui.html>
