# Playwright E2E (Phase 7 WP-G)

Local-first suite. CI is **not** enabled by default (P7-D15).

## Prerequisites

1. Backend on `:8080` (`local` profile) with Postgres/Redis/Kafka as needed.
2. Frontend: `npm run dev` → `http://localhost:5173` (or Compose frontend `:3000`).
3. Optional Stripe CLI for real pay path:
   `stripe listen --forward-to localhost:8080/api/v1/payments/webhook/stripe`

## Commands

```bash
cd frontend
npm run test:e2e
# or
E2E_BASE_URL=http://localhost:3000 npm run test:e2e
```

Credentialed flows:

```bash
set E2E_ADMIN_EMAIL=admin@example.com
set E2E_ADMIN_PASSWORD=...
set E2E_CUSTOMER_EMAIL=customer@example.com
set E2E_CUSTOMER_PASSWORD=...
npm run test:e2e
```

Unauthenticated smoke tests skip automatically if the app is down.
