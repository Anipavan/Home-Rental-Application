# Compliance Service (port 8093)

Two responsibilities:

* **RERA registration** of properties on the relevant state's RERA portal.
* **GST invoice generation** (PDF) for each captured rent payment.

## Endpoints (mounted under `/compliance`)

### RERA

| Method | Path                                       | Purpose                                               |
| ------ | ------------------------------------------ | ----------------------------------------------------- |
| POST   | `/compliance/rera/register`                | Register a property on the state RERA portal         |
| GET    | `/compliance/rera/status/{propertyId}`     | List RERA registrations (one row per state)           |
| POST   | `/compliance/lease/generate-rera/{leaseId}`| Build RERA metadata for embedding in a lease deed     |

### GST

| Method | Path                                       | Purpose                                               |
| ------ | ------------------------------------------ | ----------------------------------------------------- |
| POST   | `/compliance/gst/generate/{paymentId}`     | Generate a GST invoice for a settled payment          |
| GET    | `/compliance/gst/invoice/{id}`             | Get invoice metadata                                  |
| GET    | `/compliance/gst/invoice/{id}/pdf`         | Download the rendered PDF                             |

API Gateway exposes these under `/api/compliance/**` (StripPrefix=1).

## Kafka

* **Topic published:** `compliance-events`
    * `rera.registered`
    * `gst.invoice.generated`
* **Topic consumed:** `payment-events` — auto-generates a GST invoice on
  `payment.completed`. Idempotent (UNIQUE constraint on `payment_id`).

## GST applicability

GST applies on residential rentals only when the landlord's **annualised**
rent > ₹20 lakh (configurable via `app.compliance.gst-annual-rent-threshold`).
Below the threshold the invoice still exists with `gst_applicable=false` and
`gst_amount=0`.

## State RERA portals

`app.compliance.rera.provider` selects the active adapter:

* `MOCK` (default) — generates plausible-looking RERA numbers for dev / CI.
* (TODO) `KARNATAKA`, `MAHARASHTRA` etc. — implement `ReraPortalAdapter`
  per state portal.

## PDF rendering

Uses **OpenPDF 2.0.3** (LGPL fork of iText 4). Files are written to
`app.compliance.invoice-storage-dir` (default `uploads/gst-invoices`). In
production, swap to S3 by replacing `InvoicePdfGenerator#generate`.
