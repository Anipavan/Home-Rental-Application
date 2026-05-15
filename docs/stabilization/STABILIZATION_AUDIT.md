# Stabilization Audit — Personal Issues

> **Day 1 of the stabilization sprint.** For each flagged issue: where it lives,
> what's actually wrong, and the fix shape. File paths are clickable in most
> editors. Line numbers were taken on Day 1 — they may drift as fixes land.

Total issues: **25**. Sprint length: **~13 working days**.

---

## A. Cross-cutting (hits both tenant + owner)

### A1. Session never logs out until explicit logout
- **Where:**
  [`frontend/src/stores/auth-store.ts`](../../frontend/src/stores/auth-store.ts) (no expiry tracking)
  [`frontend/src/lib/api/client.ts`](../../frontend/src/lib/api/client.ts) (refresh-on-401 logic)
  Backend: `auth-service` JWT TTL config
- **Current behaviour:**
  - `authStore` persists `{accessToken, refreshToken}` to `localStorage` via `zustand/persist` with key `hearth-auth`.
  - **No timestamp** is stored — so we never know when the access token was issued, and the frontend never proactively refreshes / expires.
  - `client.ts` only reacts to a 401 with the `X-Token-Expired: true` header — if the backend never expires tokens, the user stays signed in indefinitely.
  - There is **no idle timeout** at all.
- **Decided fix (D5):**
  1. Backend: confirm access-token TTL = 15 min, refresh-token TTL = 7 days. Adjust `auth-service` config if not already.
  2. Frontend: extend `authStore` with `accessTokenExpiresAt` and `lastActivityAt`.
  3. Add an `IdleTimer` component in `AppShell` that tracks mouse / keyboard / focus events; auto-logout after 30 min of inactivity.
  4. Add a small countdown banner ("Session expires in 1 min") when the access token is within 60 s of expiry.

---

### A2. Search bar in top header does nothing
- **Where:** [`frontend/src/components/layout/app-shell.tsx`](../../frontend/src/components/layout/app-shell.tsx) — `<Input placeholder="Search homes, tenants, invoices…">`
- **Current:** dumb `<Input>`, no handler.
- **Decided fix (D1, real implementation):**
  - **New backend endpoint:** `GET /search?q={q}&types={homes,tenants,invoices}` exposed via api-gateway. This is a thin **search-aggregator** route in the gateway that fan-outs to:
    - `property-service /properties/search?q=…` (homes)
    - `user-service /users/search?q=…` (tenants — already exists!)
    - `payment-service /payments/search?q=…` (invoices)
  - **Frontend:** debounced (250 ms) dropdown that opens under the input, grouped by type, click-through to the entity's detail page.
  - **Role-aware:** owners search across their flats / their tenants / their invoices; tenants only across their own.
  - **Effort:** 3 days (D12–14 in the sprint).

---

### A3. Contact Support button does nothing
- **Where:** [`frontend/src/components/layout/app-shell.tsx`](../../frontend/src/components/layout/app-shell.tsx) sidebar bottom: `<Button variant="outline">Contact support</Button>`
- **Decided fix (D3):** dropdown with three options:
  1. Email — `mailto:support@anirudhhomes.in`
  2. WhatsApp — `https://wa.me/91XXXXXXXXXX?text=…`
  3. In-app form (saved to a new `support_tickets` Mongo collection in `notification-service`); admins see it under `/admin/support`.
- **Note:** real support-ticket UI is one of the simplest backend additions; included.

---

### A4. Notification bell does nothing
- **Where:** [`frontend/src/components/layout/app-shell.tsx`](../../frontend/src/components/layout/app-shell.tsx) `<Button variant="ghost" aria-label="Notifications"><Bell /></Button>`
- **Backend ready:** Notification Service (`/notifications/user/{userId}`) returns the user's logs. Already exposed via `notificationsApi`.
- **Decided fix (D2):**
  - Convert `<Button>` to a `<DropdownMenu>` showing the latest 10 notifications.
  - Unread count badge on the bell (count rows where `status != READ`).
  - "Mark all read" + per-item dismiss.
  - Polls every 60 s when the page is focused.

---

## B. Tenant flow

### B5. "My Home → Contact Owner" button broken
- **Where:** [`frontend/src/pages/tenant/my-flat.tsx`](../../frontend/src/pages/tenant/my-flat.tsx) **line 147**
  `<Button className="w-full" variant="outline"><Phone /> Contact owner</Button>`
- **Current:** no `onClick`. Just static.
- **Fix:** resolve the flat's `ownerId` → fetch owner via `usersApi.getById(ownerId)` → render a small popover with `tel:` and `mailto:` links + copy-to-clipboard fallback.

---

### B6. Lease shown twice on the page
- **Where:** [`frontend/src/pages/tenant/lease.tsx`](../../frontend/src/pages/tenant/lease.tsx)
- **Likely:** the `ordered.map` plus a stale duplicate render block. Will confirm by inspecting render order; remove the dup.

---

### B7 + B8 + B22. Lease agreement should be a downloadable PDF stored in the DB
- **Backend status:**
  - `lease-service` has `LeaseDeedPdfGenerator` (OpenPDF, generates A4 deed) — already built.
  - `document-service` exists for storage.
  - **Missing:** the lease, when signed, doesn't push the PDF to Document Service. Today the deed is written to a local volume only.
- **Fix (one PR covers all three issues):**
  1. **Backend:** in `LeaseServiceImpl.sign(...)`, after generating the deed, upload the PDF bytes to Document Service (new internal Feign client → `documents-service`), persist the resulting `documentId` on `Lease.documentUrl`.
  2. **Frontend (tenant):** replace the inline-text terms block with a simple summary card + a "Download deed (PDF)" button. PDF download via the existing `leaseApi.downloadDocument(id)` blob endpoint.
  3. **Frontend (owner):** same component on `/owner/agreements` and the new `/owner/leases` detail.
  4. **Old `agreementsApi`** stays for the signature flow (signature-pad still works), but now **owns rendering** to PDF on `sign()`.

---

### B9. Payment receipt / invoice generation not working
- **Where:** [`frontend/src/pages/tenant/payments.tsx`](../../frontend/src/pages/tenant/payments.tsx)
- **Backend status:** `compliance-service` already auto-generates GST invoices on `payment.completed` (Kafka consumer). Each invoice has a downloadable PDF.
- **Missing on frontend:**
  - For each completed payment row, show **Download invoice** + **Download receipt** buttons.
  - Receipt = a much simpler one-pager that the payment-service generates (need to add: 1 day of backend work).
- **Fix:**
  1. Backend: `payment-service` adds a `/payments/{id}/receipt/pdf` endpoint that returns a printable receipt PDF.
  2. Backend: a `paymentApi.findInvoiceByPaymentId(paymentId)` Feign-or-direct query against `compliance-service`.
  3. Frontend: two new buttons on each completed row.

---

### B10–B12. Profile autofill / edit mode / save
- **Where:** [`frontend/src/pages/tenant/profile.tsx`](../../frontend/src/pages/tenant/profile.tsx)
- **Current:**
  - Form `<Field defaultValue={q.data?.firstName}>` does pre-populate when the query lands — **autofill actually works** today.
  - But **fields are always editable** — no view-mode → edit-mode toggle. User probably hit "save" without intending to and got confused.
  - Save call (`updateM.mutate`) does work.
- **Fix:**
  1. Add a top-right "Edit" / "Save" / "Cancel" button trio.
  2. Default to **read-only** view (using `<dl>` rather than `<input>`).
  3. Click "Edit" → swap to inputs.
  4. "Save" → mutate; revert to view mode on success.
  5. "Cancel" → discard local edits.

---

### B13. Profile → ID verification upload not working
- **Where:** [`frontend/src/pages/tenant/profile.tsx`](../../frontend/src/pages/tenant/profile.tsx) **line 206** uses `usersApi.uploadDocument(userId, file, "ID_PROOF")`
- **Why broken:** `user-service /users/{id}/documents` exists but writes to a local upload dir on user-service. The new **Document Service** is the right home for this — it has OCR, pre-signed downloads, KYC integration.
- **Fix:**
  1. Switch the upload call to `documentsApi.upload(userId, "AADHAAR" | "PAN", file)`.
  2. Use the document's pre-signed URL for view/download.
  3. On upload, optionally trigger `documentsApi.extract(id)` to OCR the ID — fields auto-fill the user's profile via the existing `DocumentEventListener` in `user-service`.

---

### B14. Profile picture upload not working
- **Where:** Same file, line 99 `usersApi.uploadDocument(...)` with `type: "PROFILE"`
- **Why broken:** Same root cause as B13 + `User.profilePictureUrl` isn't being updated to the returned URL.
- **Fix:** Switch to `documentsApi.upload(userId, "PHOTO", file)`. After success, update `User.profilePictureUrl` via `usersApi.update`. Render via the document's pre-signed URL.

---

## C. Owner flow

### C15. Add Building → state/city auto-suggest
- **Where:** [`frontend/src/pages/owner/building-new.tsx`](../../frontend/src/pages/owner/building-new.tsx) (plain `<Field>` for both)
- **Decided fix (D4 — DB-backed):**
  1. **DB schema** in `property-service`:
     ```sql
     CREATE TABLE ref_states (
         id          BIGINT PRIMARY KEY,
         code        VARCHAR(10) UNIQUE,
         name        VARCHAR(100) NOT NULL
     );
     CREATE TABLE ref_cities (
         id          BIGINT PRIMARY KEY,
         state_id    BIGINT NOT NULL REFERENCES ref_states(id),
         name        VARCHAR(150) NOT NULL,
         tier        SMALLINT,
         UNIQUE (state_id, name)
     );
     ```
  2. **Flyway migration** seeds all 28 states + ~600 cities (one-time, from a static CSV in `db/migration/data/india_geo.csv`).
  3. **REST endpoints:**
     - `GET /properties/reference/states` → list of states (id, code, name)
     - `GET /properties/reference/cities?stateId={id}` → cities for that state
     - `GET /properties/reference/cities/search?q={q}` → for free-text auto-suggest
  4. **Frontend:**
     - State `<Select>` populated by `referenceApi.states()`.
     - City `<Combobox>` (autocomplete) populated by `referenceApi.cities(stateId)`, reactive to state.
     - Both store the **id**, not the name (DB-correct, MVC-style as you asked).
  5. **Buildings table** gets `state_id` + `city_id` foreign keys (in addition to the existing string columns for backwards compatibility).

---

### C16. Add Building → no image upload
- **Where:** Same file. Existing endpoint `propertiesApi.buildings.uploadImage(id, file)` already works on building-**detail**, but not on building-**new**.
- **Fix:** add a `<FileUpload multiple accept="image/*">` block. After `m.mutate(...)` succeeds, sequentially upload images to the new building's id, then redirect to the detail page.

---

### C17. Buildings → click → flats not visible ("Not Found")
- **Where:** [`frontend/src/pages/owner/building-detail.tsx`](../../frontend/src/pages/owner/building-detail.tsx) **line 18**
  `const buildingId = Number(id);`
- **🔴 Real bug confirmed:** `Building.buildingId` is a **`String` UUID** in the backend (see `Building.java`). Coercing to `Number` produces `NaN`, the request goes to `/properties/buildings/NaN`, returns 404 → "Not Found".
- **Fix:** drop the `Number(...)` coercion. Use `id` (string) directly. **Same fix needed in:** `frontend/src/pages/public/property-detail.tsx:37` for `flatId`.

---

### C18. Tenants page → no clickable detail view
- **Where:** [`frontend/src/pages/owner/tenants.tsx`](../../frontend/src/pages/owner/tenants.tsx)
- **Current:** flat list of tenant cards, no click-through.
- **Fix:** new route `/owner/tenants/:tenantId` showing:
  - Header card: photo, name, phone (tel:), email (mailto:), KYC status badge
  - Active lease summary
  - Payment history table (rent paid / overdue / upcoming) — `paymentsApi.byTenant`
  - Maintenance ticket history — `maintenanceApi.byTenant`
  - Documents on file — `documentsApi.byUser`
  - "Send notification" + "Call" + "Email" actions

---

### C19. Calling owner / tenant button broken
- **Where:** owner buttons that say "Call" — currently no `tel:` href.
- **Fix:** wrap in `<a href={`tel:${user.phone}`}>` or wire `onClick={() => window.location.href = `tel:${user.phone}`}`. Trivial.

---

### C20 + C21 + C23. Payments display: flat ID instead of number, tenant ID instead of name
- **Where:**
  - [`frontend/src/pages/owner/payments.tsx`](../../frontend/src/pages/owner/payments.tsx) lines **274, 275, 360**
  - [`frontend/src/pages/tenant/pay.tsx`](../../frontend/src/pages/tenant/pay.tsx) line **646** ("For flat #{flatId}")
- **Fix:** introduce a small `useFlatLookup(flatIds: string[])` hook that bulk-fetches `propertiesApi.flats.get(id)` once and caches by id. Replace `#{p.flatId}` with `#{lookup.get(p.flatId)?.flatNumber ?? p.flatId}`. Same pattern for tenant names via `usersApi.byAuthId`.

---

### C22. Agreements page should display documents
- Same fix as B7/B8/B22 above.

---

## D. Payment integration

### D24. PhonePe / GPay / Paytm "Open app" links broken
- **Where:** [`frontend/src/pages/tenant/pay.tsx`](../../frontend/src/pages/tenant/pay.tsx) **lines 403, 444–453**
- **Current:** the frontend trusts `response?.upiIntentUrl` from the backend. Likely empty/null because the mock `paymentGateway.initiate(...)` doesn't return one for the UPI app cases.
- **Fix:**
  1. Backend: when `paymentMethod=UPI` + `upiApp` is set, the mock gateway must populate `upiIntentUrl` with a valid UPI deep link:
     - **PhonePe:** `phonepe://pay?pa={vpa}&pn={payee}&am={amt}&cu=INR&tr={txnRef}&tn={note}`
     - **GPay:** `tez://upi/pay?pa=…&pn=…&am=…&cu=INR&tn=…`
     - **Paytm:** `paytmmp://pay?pa=…&pn=…&am=…&cu=INR&tn=…`
     - **Generic:** `upi://pay?pa=…&pn=…&am=…&cu=INR&tn=…` (fallback)
  2. Backend: configure a **test merchant VPA** (e.g. `merchant@upi`) in `payment-service`'s application.yaml as `app.payments.upi.merchant-vpa`.
  3. Frontend: only show the "Open …" link when `upiIntentUrl` is non-empty. Already handled — once backend populates, the UI works.

---

### D24-display. "For flat #{flatId}" shows raw ID
- **Where:** [`frontend/src/pages/tenant/pay.tsx`](../../frontend/src/pages/tenant/pay.tsx) line 646
- **Fix:** look up via `propertiesApi.flats.get(flatId)` and render `#{flat.flatNumber}`.

---

### D25. Cards / Net Banking redirect broken
- **Where:** [`frontend/src/pages/tenant/pay.tsx`](../../frontend/src/pages/tenant/pay.tsx) **line 508**
  `if (res.redirectUrl) { window.location.href = res.redirectUrl; }`
- **Current:** if `redirectUrl` is missing, frontend shows a toast and gives up. Backend's mock probably doesn't return one for `CARD` / `NET_BANKING`.
- **Fix:**
  1. Backend mock returns a redirect URL pointing at a **mock checkout page** hosted by the payment-service itself: `/payments/mock-checkout?orderId=…&amount=…`.
  2. The mock checkout page has Approve / Decline buttons that POST back to a `/payments/mock-callback` endpoint — same shape the production callback would have.
  3. Frontend already handles `redirectUrl` correctly; nothing changes there.

---

## E. Items I noticed in passing (not on your list — only fixing if they're on the path)

- **`frontend/src/pages/public/property-detail.tsx:37`** — same `Number(id)` bug as C17. Same fix.
- **Toast "Couldn't render PDF"** can swallow real errors (no error code surfaced) — minor cleanup along the way.
- **`zustand/persist`** stores tokens in `localStorage` (XSS-readable). Out of scope for this sprint — but worth flagging for a future "secure storage" pass (httpOnly cookies via the gateway).

---

## Summary by area

| Area | # Issues | Day(s) | Riskiest |
| --- | --- | --- | --- |
| Cross-cutting (session / search / bell / support) | 4 | D2–D3 + D12–14 | Search aggregator (multi-service) |
| Tenant flow | 10 | D4–D6 | Lease PDF storage flow |
| Owner flow | 8 | D7–D10 | State/City reference DB seeding |
| Payment integration | 3 | D11 | UPI deep-link param correctness |

## What's unchanged in this sprint
- AI features — out of scope
- PG / Office — out of scope
- New design system — only fix the listed bugs

---

*Audit generated on Day 1 of the stabilization sprint. Update line numbers as code drifts.*
