# Phase 7 WP-C Completion Report — Checkout & Stripe Elements

**Date:** 2026-11-05  
**Status:** Implementation complete — continuing automatically to WP-D (workflow §10.1)  
**Scope:** Customer checkout, pay (Stripe Elements Test Mode), orders, unpaid cancel

---

## 1. Features implemented

| Feature | Status |
|---------|--------|
| Shipping address form → `shippingAddressJson` | Done |
| Optional promotion code + `/cart/promo` preview | Done |
| Order estimate from cart; authoritative totals from checkout/pay APIs | Done |
| `POST /orders/checkout` + `Idempotency-Key` persistence/retry | Done |
| Pay via `POST /orders/{id}/pay` + Payment Element | Done |
| Poll order/payment; webhook sole PAID authority | Done |
| Orders list/detail; cancel unpaid; resume pay | Done |
| `clientSecret` memory-only; Test Mode `pk_test_` only | Done |

Checkout / Stripe Live / admin / shipping fulfillment / refunds: **not** in WP-C (WP-D+).

---

## 2. UI routes

| Route | Access |
|-------|--------|
| `/checkout` | CUSTOMER |
| `/orders` | CUSTOMER |
| `/orders/:id` | CUSTOMER |
| `/orders/:id/pay` | CUSTOMER |

---

## 3. API endpoints used (existing)

`POST /orders/checkout`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`,  
`POST /orders/{id}/pay`, `GET /orders/{id}/payment`, `POST /cart/promo`

**No new backend APIs.**

---

## 4. Packages

Added: `@stripe/stripe-js`, `@stripe/react-stripe-js`, `@testing-library/user-event` (dev).

---

## 5. Verification

| Command | Result |
|---------|--------|
| `npm test` | **63 passed** |
| `npm run build` | OK |
| `npm run lint` | OK |
| `npm audit` | **0** vulnerabilities |

### Manual Stripe Test Mode checklist

1. Backend `STRIPE_MOCK=false` + test keys; Stripe CLI forward to `/api/v1/payments/webhook/stripe`.  
2. FE `VITE_STRIPE_PUBLISHABLE_KEY=pk_test_…`.  
3. CUSTOMER: cart → checkout → pay with `4242…` → poll until order `PAID`.  
4. Decline card → order remains `PENDING_PAYMENT`; resume pay.  
5. Reload `/orders/:id/pay` → `/pay` reuses PI.  
6. Confirm GET payment responses omit `clientSecret`.

---

## 6. Known limitations

- Free-form shipping JSON (no backend schema validation).  
- No dedicated payments index API (order-centric history).  
- Playwright E2E deferred to WP-G.  
- Full Stripe CLI happy path requires local infra + keys (not run in this CI unit pass).

---

## 7. Confirmations

- Phase 1–6 backend unchanged in WP-C.  
- `confirmPayment` success is not treated as PAID.  
- Continuing to WP-D per revised Phase 7 workflow.
