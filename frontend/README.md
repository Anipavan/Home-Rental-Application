# Hearth — Home Rental Frontend

A modern, NoBroker-inspired UI for the Home Rental microservices backend.

## Stack

- React 18 + TypeScript + Vite
- Tailwind CSS + shadcn/ui primitives
- React Router v6
- TanStack Query (server state)
- Zustand (auth state)
- Recharts (analytics)

## Run

```bash
npm install
npm run dev
```

Opens on **http://localhost:4200** — already in the backend's CORS allowlist, no env changes needed.

By default, requests go to `http://localhost:8080/rentals/v1` (the API gateway). Override via `.env`:

```
VITE_API_BASE_URL=http://localhost:8080/rentals/v1
```

## Pages

### Public
- `/` — Landing
- `/browse` — Property listings with filters
- `/property/:id` — Listing detail
- `/login`, `/register`, `/forgot-password`

### Tenant (`/app/*`)
- Dashboard with rent-due banner
- My home, lease details
- Payments list + checkout flow (PhonePe / GPay / Paytm / UPI / Card / Cash)
- Maintenance: list, raise new, comment thread
- Profile

### Owner (`/owner/*`)
- Dashboard with revenue chart + KPIs
- Buildings (CRUD), building detail, flats (CRUD)
- Tenants, payments, maintenance queue
- Analytics (revenue + occupancy)

## Payment integration

The PhonePe / GPay / Paytm / UPI / Card flows are **frontend-mocked** today
(`src/lib/api/payment-gateway-mock.ts`). The mock matches the eventual real-API
shape — once backend ships `POST /payments/initiate` and `POST /payments/verify`,
swap the import and the UI works unchanged.

Cash payments use the real `POST /payments/{id}/pay-cash` endpoint.

## Auth

JWT bearer + refresh token. Tokens persist in `localStorage` via Zustand
`persist`. The axios client transparently refreshes on 401 with
`X-Token-Expired: true` and replays the original request.
