# Document Service (port 8091)

Owns all document storage, OCR, and pre-signed download URLs.

## Endpoints (mounted under `/documents`)

| Method | Path                               | Purpose                                                 |
| ------ | ---------------------------------- | ------------------------------------------------------- |
| POST   | `/documents/upload`                | Multipart upload (publishes `document.uploaded`)        |
| GET    | `/documents/{id}`                  | Get metadata                                            |
| GET    | `/documents/user/{userId}`         | List user's active documents                            |
| GET    | `/documents/{id}/download`         | Pre-signed URL (15-min TTL)                             |
| GET    | `/documents/{id}/blob`             | Stream binary (signature-verified — used by the URL)    |
| POST   | `/documents/{id}/extract`          | Run OCR / Document AI (publishes `document.extracted`)  |
| GET    | `/documents/{id}/extracted-data`   | Fetch extracted fields                                  |
| POST   | `/documents/{id}/verify`           | Admin / KYC mark verified (publishes `document.verified`) |
| DELETE | `/documents/{id}`                  | Soft-delete                                             |

API Gateway exposes these under `/api/documents/**` (StripPrefix=1).

## Kafka

* **Topic published:** `document-events`
    * `document.uploaded`
    * `document.verified`
    * `document.extracted`

## Storage

`app.documents.storage-backend`:

* `LOCAL` (default) — files under `app.documents.local-dir/{userId}/{docId}_{name}`.
* `S3` — stub. Wire AWS SDK before going to production.

## Pre-signed URLs

HMAC-SHA256-signed `?expires={epoch}&signature={base64}` query params,
TTL controlled by `app.documents.download-url-ttl-seconds` (default 900s
= 15 min, matching the architecture's security guidance). Verified in
`PreSignedUrlSigner` with constant-time equality.

## OCR

`app.documents.ocr.provider`:

* `STUB` (default) — deterministic dev stub returning plausible fields per
  document type.
* `TESSERACT` — plug in by adding a new `OcrEngine` bean with
  `@ConditionalOnProperty(provider="TESSERACT")`. Native Tesseract binaries
  required.

## Allowed content types

`app.documents.allowed-content-types` — defaults to
`application/pdf, image/png, image/jpeg, image/jpg`.
