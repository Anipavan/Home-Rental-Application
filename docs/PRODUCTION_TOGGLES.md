# Production Toggles — India Compliance Layer

The four India Compliance Layer services ship with safe **MOCK / STUB / LOCAL**
defaults so the platform comes up end-to-end on a developer laptop without any
real provider accounts. To go to production you flip three sets of toggles
described below.

The toggles are **environment variables**, all read by Spring Boot's standard
externalised-configuration mechanism. Set them in `docker-compose.yml`, your
Kubernetes manifest, or an `.env` file at the repository root.

---

## 1. Digio (Aadhaar / DigiLocker / PAN KYC)

KYC Service — `KYCService` — port 8092.

### What changes

* `app.kyc.provider` switches from `MOCK` to `DIGIO`.
* The `DigioKycProvider` bean activates (gated by
  `@ConditionalOnProperty(prefix="app.kyc", name="provider", havingValue="DIGIO")`).
* All KYC initiation requests now hit Digio's REST API; success / failure
  is delivered back via Digio's webhook to `POST /api/kyc/webhook/digio`.

### Required env vars

| Variable                | Example                                               | Notes                                          |
| ----------------------- | ----------------------------------------------------- | ---------------------------------------------- |
| `KYC_PROVIDER`          | `DIGIO`                                               | Default `MOCK`.                                |
| `KYC_AADHAAR_SALT`      | 32+ random bytes (base64)                             | Per-environment pepper for Aadhaar SHA-256.    |
| `DIGIO_BASE_URL`        | `https://api.digio.in`                                | Digio's prod URL; same in staging.             |
| `DIGIO_API_KEY`         | `eyJhbGciOiJSUzI1NiIs…` (Digio-issued JWT)            | Bearer token in `Authorization` header.        |
| `DIGIO_CLIENT_ID`       | UUID from Digio dashboard                              | Sent in `X-Digio-Client-Id` header.            |
| `DIGIO_CALLBACK_URL`    | `https://api.rentgenius.in/api/kyc/webhook/digio`     | Public URL — Digio must reach it.              |

### Webhook signature verification

[KycController](../KYCService/src/main/java/com/spa/home_rental_application/kyc_service/controller/KycController.java)
already accepts the `X-Digio-Signature` header. Before turning the provider
on in production, **wire the HMAC verification** in
`handleDigioCallback` — Digio docs publish the signing recipe; replace the
existing log-only line with a constant-time comparison against the
expected HMAC.

### Stop list

Until you have provider creds and a webhook signature implementation:

```bash
KYC_PROVIDER=MOCK   # any other value will reject at startup
```

---

## 2. AWS S3 (Document storage)

Document Service — `DocumentationService` — port 8091.

### What changes

* `app.documents.storage-backend` switches from `LOCAL` to `S3`.
* The `S3DocumentStorage` bean activates and the `LocalDocumentStorage`
  bean is excluded.
* `DocumentStorage#store` writes to
  `s3://{bucket}/{prefix}users/{userId}/{docId}_{filename}` with AES-256
  server-side encryption.

### Required env vars

| Variable                    | Example                          | Notes                                              |
| --------------------------- | -------------------------------- | -------------------------------------------------- |
| `DOCUMENT_STORAGE_BACKEND`  | `S3`                             | Default `LOCAL`.                                   |
| `DOCUMENT_S3_BUCKET`        | `rentgenius-documents-prod`      | Must already exist; must allow `s3:PutObject`/`s3:GetObject`/`s3:DeleteObject` for the IAM principal. |
| `DOCUMENT_S3_REGION`        | `ap-south-1`                     | Mumbai is closest for India.                       |
| `DOCUMENT_S3_KEY_PREFIX`    | `prod/`                          | Optional; useful for sharing one bucket across envs. |
| `DOCUMENT_S3_ENDPOINT`      | (blank for AWS)                  | Set to `http://minio:9000` for MinIO / LocalStack. |
| `DOCUMENT_S3_PATH_STYLE`    | `false` (AWS) / `true` (MinIO)   | Path-style addressing toggle.                      |

### IAM credentials

The `S3DocumentStorage` uses the **AWS default credential chain**
(`software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider`).
In order of preference:

1. `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` env vars.
2. `~/.aws/credentials` profile config (rare in containers).
3. **EC2 / ECS / EKS instance profile** (the production path — no secrets in image).

### Bucket policy hardening

Enable on the bucket itself (Terraform / Console):

* **Block all public access** (S3 → Permissions → Block public access).
* **Bucket policy** denying any request with `aws:SecureTransport=false`.
* **Lifecycle rule** to auto-expire orphaned uploads older than 365 days.
* **Versioning + MFA delete** on production-sensitive prefixes.

### Object access

Documents are downloaded via **HMAC pre-signed URLs** issued by
`PreSignedUrlSigner` (15-minute TTL). Note: this is a Document-Service-issued
HMAC, not an AWS pre-signed URL. The HMAC URL points back to
`/documents/{id}/blob`, which streams from S3 server-side. If you'd prefer
direct-from-S3 downloads (lower egress through the service), swap to
`software.amazon.awssdk.services.s3.presigner.S3Presigner` — drop-in
replacement for the pre-signer.

---

## 3. Tesseract OCR (Document field extraction)

Document Service — `DocumentationService` — port 8091.

### What changes

* `app.documents.ocr.provider` switches from `STUB` to `TESSERACT`.
* The `TesseractOcrEngine` bean activates and runs real OCR against
  uploaded blobs (any backend — LOCAL or S3).
* Aadhaar / PAN extraction uses regex heuristics in
  [TesseractOcrEngine#extractFields](../DocumentationService/src/main/java/com/spa/home_rental_application/document_service/ocr/TesseractOcrEngine.java).

### Required env vars

| Variable                   | Example                  | Notes                                        |
| -------------------------- | ------------------------ | -------------------------------------------- |
| `DOCUMENT_OCR_PROVIDER`    | `TESSERACT`              | Default `STUB`.                              |
| `TESSDATA_PREFIX`          | `/usr/share/tessdata`    | Path to the language data files.             |

### Native runtime requirements

The Document Service Docker image already installs:

* `tesseract-ocr` — the native binary.
* `tesseract-ocr-data-eng` — English (Aadhaar back, PAN, agreements).
* `tesseract-ocr-data-hin` — Hindi (Aadhaar front).
* `libgomp` — OpenMP runtime that tess4j links against.

If you switch to a **non-Alpine base image**, mirror the install:

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr tesseract-ocr-eng tesseract-ocr-hin \
    && rm -rf /var/lib/apt/lists/*
```

### Accuracy upgrade path

The current implementation is regex-driven and good enough for clean
Aadhaar / PAN scans. For higher accuracy in production:

1. **Train a custom Tesseract model** on Aadhaar layouts.
2. **Use Google Document AI** (or Claude vision via the AI Gateway —
   architecture port 8089) for layout-aware extraction, and keep the
   `TesseractOcrEngine` as the cheap fallback when the Document AI path
   is rate-limited.
3. **Add a confidence threshold** — currently the field-count heuristic
   in `TesseractOcrEngine` returns 0.30–0.85. Replace with the
   word-level confidence API from tess4j and gate downstream
   auto-fill at e.g. ≥0.75.

---

## 4. End-to-end production checklist

Before flipping all three:

- [ ] Set the secrets in your secret manager (AWS Secrets Manager, Vault,
      or Kubernetes `Secret` resources). Never bake into images.
- [ ] **Rotate** `KYC_AADHAAR_SALT` and `DOCUMENT_DOWNLOAD_SECRET` to
      production-only values.
- [ ] **Implement** Digio webhook HMAC verification in
      `KycController.handleDigioCallback`. (Currently logs only.)
- [ ] **Lock down** the S3 bucket per the hardening checklist above.
- [ ] **Smoke-test** with `KYC_PROVIDER=DIGIO` in staging — submit an
      Aadhaar test number Digio publishes for sandbox use.
- [ ] **Smoke-test** with a real PAN card image through `/documents/upload`
      → `/documents/{id}/extract` → confirm `panNumber` is extracted and
      the `document.extracted` event lands in `document-events`.
- [ ] **Replay-test** that User Service auto-fills the user's profile when
      `document.extracted` is observed (`KycEventListener` +
      `DocumentEventListener` in `user-service`).
- [ ] **Verify** that GST invoices are still generated on
      `payment.completed` and that the PDF survives a container restart
      (volume mount `gst-invoices` is healthy).

---

## 5. Reverting to safe defaults

Every toggle is a single env-var flip. The compose file ships defaults that
keep everything in safe mode:

```yaml
KYC_PROVIDER: ${KYC_PROVIDER:-MOCK}
DOCUMENT_STORAGE_BACKEND: ${DOCUMENT_STORAGE_BACKEND:-LOCAL}
DOCUMENT_OCR_PROVIDER: ${DOCUMENT_OCR_PROVIDER:-STUB}
RERA_PROVIDER: ${RERA_PROVIDER:-MOCK}
```

If a production rollout misbehaves, scaling back to MOCK / LOCAL / STUB at
the orchestrator level is safe — the persistence layer is the same in both
modes (the database row stays valid, only the verification provider changes).
