# Lease Service (port 8090)

Owns the lease lifecycle for the platform.

## Lifecycle

```
DRAFT  ──/sign──▶  ACTIVE  ──/renew──▶  ACTIVE   (extended end date)
                       │
                       ├──/terminate──▶  TERMINATED
                       │
                       └─ end-date passes (cron) ──▶  EXPIRED
```

## Endpoints (mounted under `/lease`)

| Method | Path                                       | Purpose                                          |
| ------ | ------------------------------------------ | ------------------------------------------------ |
| POST   | `/lease/leases`                            | Create new lease (DRAFT)                         |
| GET    | `/lease/leases/{id}`                       | Get lease by id                                  |
| GET    | `/lease/leases/tenant/{tenantId}`          | Tenant's leases                                  |
| GET    | `/lease/leases/flat/{flatId}`              | Lease history for a flat                         |
| PUT    | `/lease/leases/{id}/renew`                 | Renew (publishes `lease.renewed`)                |
| PUT    | `/lease/leases/{id}/terminate`             | Terminate (publishes `lease.terminated`)         |
| POST   | `/lease/leases/{id}/sign`                  | Sign DRAFT → ACTIVE (publishes `lease.signed`)   |
| GET    | `/lease/leases/{id}/document`              | Download lease deed PDF                          |
| GET    | `/lease/expiring?days=60`                  | Leases expiring in next N days                   |
| POST   | `/lease/leases/generate-rera/{id}?state=…` | Generate RERA-stamped deed (calls Compliance)    |
| GET    | `/lease/leases/{id}/history`               | Audit history                                    |

API Gateway exposes these under `/api/lease/**` (StripPrefix=1).

## Kafka

* **Topic published:** `lease-events`
    * `lease.signed`
    * `lease.expiring` (cron)
    * `lease.renewed`
    * `lease.terminated`
* **Topic consumed:** `property-events` — `flat.vacated` auto-terminates the
  ACTIVE lease for that flat.

## Daily expiry cron

Runs at `app.lease.expiry-cron` (default `0 0 2 * * *`):

1. For ACTIVE leases ending within `app.lease.expiry-warning-days` (default 60),
   emit `lease.expiring` and stamp `expiry_warning_sent_at` (idempotent).
2. For ACTIVE leases past their `end_date`, flip status to `EXPIRED`.

## RERA stamping

Lease creation tries to fetch a RERA stamp from Compliance Service via Feign.
If Compliance is unavailable, the circuit breaker opens and we fall back to
"RERA: pending" — the deed is still generated and can be re-stamped later
via `POST /lease/leases/generate-rera/{id}?state=…`.
