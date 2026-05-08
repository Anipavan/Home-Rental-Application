# Stabilization Sprint — Tracker

A simple checkbox board. Tick items as they ship. **One column per day**, fully sequential. Reorder freely if priorities shift.

---

## Day 1 — Audit ✅

- [x] Read every flagged screen + backend touchpoint
- [x] Write [STABILIZATION_AUDIT.md](STABILIZATION_AUDIT.md) — file:line for each of 25 issues
- [x] Write this tracker

## Day 2–3 — Cross-cutting (4 issues) ✅

- [x] **A1** — Session expiry & idle-timeout logout
  - [x] Backend: tightened `auth-service` to 15-min access + 7-day refresh
  - [x] Frontend: `authStore` extended with `accessTokenExpiresAt`, `lastActivityAt`
  - [x] `IdleTimer` (mouse / keyboard / focus / scroll / touch tracking)
  - [x] 30-min auto-logout
  - [x] 60-s expiry warning banner
- [x] **A4** — Notification bell wired to Notification Service
  - [x] `NotificationBell` dropdown in AppShell
  - [x] Fetches latest 10 via `notificationsApi.byUser`
  - [x] Unread badge
  - [x] 60-s polling when focused
  - _Per-item dismiss + mark-all-read deferred — needs a server-side `read` flag._
- [x] **A3** — Contact Support
  - [x] Email + WhatsApp + in-app form options
  - [x] New `support_tickets` Mongo collection in notification-service
  - [x] Admin view at `/admin/support` with respond + status update
  - [x] Sidebar "Contact support" button wired

## Day 4 — Contact owner / call buttons (2 issues)

- [ ] **B5** — `MyFlatPage` → "Contact owner" popover
  - [ ] Resolve owner via `usersApi.getById(ownerId)`
  - [ ] Tel + email + copy-to-clipboard
- [ ] **C19** — Owner-side "Call" button — `tel:` link
- [ ] (Hide search bar feature flag during this work; un-hide on Day 12)

## Day 5 — Profile screen (5 issues)

- [ ] **B10/B11/B12** — View / edit toggle, autofill, save
  - [ ] Read-only `<dl>` mode by default
  - [ ] Edit / Save / Cancel button trio
- [ ] **B13** — ID upload via Document Service
  - [ ] Switch from `usersApi.uploadDocument` to `documentsApi.upload(...,"AADHAAR"|"PAN",...)`
  - [ ] On upload, optionally trigger `documentsApi.extract(id)`
- [ ] **B14** — Profile pic via Document Service
  - [ ] Switch to `documentsApi.upload(...,"PHOTO",...)`
  - [ ] Update `User.profilePictureUrl` to pre-signed URL

## Day 6 — Lease + agreement PDF (4 issues)

- [ ] **B6** — Fix duplicate render
- [ ] **B7/B8/C22** — PDF generation + storage
  - [ ] Backend: `LeaseServiceImpl.sign(...)` pushes deed to Document Service
  - [ ] Backend: persist resulting `documentId` on `Lease.documentUrl`
  - [ ] Frontend (tenant): summary card + "Download deed (PDF)" button
  - [ ] Frontend (owner): same component on `/owner/agreements` + `/owner/leases`
- [ ] **B9** — Receipt + invoice generation
  - [ ] Backend: new `payment-service /payments/{id}/receipt/pdf` endpoint
  - [ ] Backend: cross-service `findInvoiceByPaymentId` lookup
  - [ ] Frontend: per-row "Download invoice" / "Download receipt" buttons

## Day 7 — Add Building (2 issues)

- [ ] **C15** — DB-backed state/city auto-suggest
  - [ ] Flyway migration: `ref_states` + `ref_cities` tables
  - [ ] Seed CSV: 28 states + ~600 cities (`db/migration/data/india_geo.csv`)
  - [ ] REST: `GET /properties/reference/states`
  - [ ] REST: `GET /properties/reference/cities?stateId=…`
  - [ ] REST: `GET /properties/reference/cities/search?q=…`
  - [ ] Frontend: `<StateSelect>` + `<CitySelect>` cascade combobox
  - [ ] Add `stateId` + `cityId` FKs on `buildings` (keep existing string columns for back-compat)
- [ ] **C16** — Image upload on building creation
  - [ ] Multi-image FileUpload block on building-new
  - [ ] Sequential upload after building creates → redirect to detail

## Day 8 — Buildings list "Not Found" (1 issue)

- [ ] **C17** — Drop `Number(id)` coercion in `building-detail.tsx:18` and `property-detail.tsx:37`
  - [ ] Use string ids throughout
  - [ ] Smoke-test: click into a building, see flats list

## Day 9 — Tenant detail page (1 issue)

- [ ] **C18** — New route `/owner/tenants/:tenantId`
  - [ ] Header card: photo, name, phone (tel:), email (mailto:), KYC badge
  - [ ] Active lease summary
  - [ ] Payment history (paid / overdue / upcoming)
  - [ ] Maintenance ticket history
  - [ ] Documents on file
  - [ ] Send notification + Call + Email actions

## Day 10 — Payment displays + receipts/invoices (3 issues) ✅

- [x] **C20/C21/C23** — Resolve flatId → flatNumber + tenantId → name
  - [x] New `useFlatLookup(flatIds[])` and `useUserLookup(userIds[])` hooks
  - [x] Replace raw IDs across owner-payments, tenant-pay, tenant-payments
- [x] Backend: new `PaymentPdfGenerator` (OpenPDF) + `/payments/{id}/invoice.pdf` and
      `/payments/{id}/receipt.pdf` endpoints streaming PDF bytes
- [x] Backend: compliance `/compliance/gst/invoice/by-payment/{paymentId}` lookup
- [x] Frontend: per-row Download Receipt + Download Invoice buttons on
      owner-payments table; tenant DueCard gets an Invoice button; tenant
      HistoryRow's receipt button finally works; PaySuccess "Download
      receipt" wired to the new endpoint

## Day 11 — UPI deep links + Cards/Net Banking redirect (2 issues) ✅

- [x] **D24** — UPI deep links
  - [x] Backend: `payment-service` mock + razorpay gateways already
        populate `upiIntentUrl` per app
    - PhonePe: `phonepe://pay?…`
    - GPay: `tez://upi/pay?…`
    - Paytm: `paytmmp://pay?…`
    - Fallback: `upi://pay?…`
  - [x] Frontend: prominent "Open <App>" gradient button + auto-redirect
        on mobile user-agent ~250ms after intent URL arrives
- [x] **D25** — Cards / Net Banking redirect
  - [x] Backend: mock returns redirectUrl to `/payments/mock/checkout?...`
        with method, amount, orderId, paymentId, returnUrl
  - [x] Backend: hosted HTML checkout page with Pay / Cancel that
        redirects back to tenant `returnUrl` with verification params
  - [x] Mock callback flow lands on existing `PaymentReturnPage` which
        already calls `/payments/verify`

## Day 12–14 — Real search bar (1 issue) ✅

- [x] **A2** — Cross-entity search
  - [x] Property: `GET /properties/buildings/search?q=&ownerId=&limit=`
        (case-insensitive contains on name / address / city / state)
  - [x] User: existing `GET /users/search/{q}`
  - [x] Payment: deferred (UUIDs aren't human-searchable; tenant-name
        search already finds the right rows)
  - [x] Role-aware filtering: OWNER scoped to own buildings, ADMIN sees
        everything, TENANT search hidden
  - [x] Frontend: `<GlobalSearch />` debounced 250 ms dropdown grouped
        by type with empty / loading / no-hits states
  - [x] ⌘K / Ctrl-K to focus, Esc to close, click-outside dismisses
  - [x] Click-through to detail page (owner buildings, tenants, etc.)

## Day 15 — Buffer + smoke pass ✅

- [x] `npx tsc --noEmit` clean across the frontend
- [x] `mvn package` clean for property / payment / compliance services
- [x] Update tracker
- [ ] Walk every flow as a real user (tenant + owner + admin) — _next user pass_
- [ ] Screenshot before/after grid for the demo
- [ ] Lighthouse score check on key pages

---

## Done means
- Every checkbox above is ticked
- A non-technical person can complete owner onboarding → flat creation → tenant onboarding → rent payment → invoice download with no developer help
- All 25 issues from the personal list are verified fixed
