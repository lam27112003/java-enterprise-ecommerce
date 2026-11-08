# Phase 6 Implementation Plan — Payment, Events & Notifications

**Date:** 2026-11-04 (decision lock complete)  
**Status:** **IMPLEMENTED + HARDENED** (P6-D1…D11) — see `docs/PHASE6_COMPLETION_REPORT.md`; awaiting Phase 7 approval  
**Sources:** `DEVELOPMENT_PLAN.md`, `TASK_TRACKER.md`, `docs/API_DOCUMENTATION.md`, `docs/SECURITY.md`, Phase 5 completion/locks, user decision locks

---

## 0. Decision lock (final — P6-D1…D11)

| ID | Decision | Status |
|----|----------|--------|
| **P6-D1** | Stripe **PaymentIntents** + client secrets (Elements). Not Checkout Session. | **APPROVED** |
| **P6-D2** | Separate `POST …/pay` after Phase 5 checkout. Checkout does **not** create intents. | **APPROVED** |
| **P6-D3** | Late success on `CANCELLED` / `PAYMENT_FAILED`: **never** → `PAID`, **never** commit inventory. **Record** success + start **safe refund / reconciliation**. Duplicate webhooks must **not** duplicate refunds. | **APPROVED** |
| **P6-D4** | **Kafka + Mailhog** in Phase 6. Publish via **transactional outbox** after DB commit. Consumers **idempotent**. | **APPROVED** |
| **P6-D5** | Defer admin shipping status APIs (`PROCESSING` / `SHIPPED` / `DELIVERED`) to **Phase 7**. | **APPROVED** |
| **P6-D6** | Duplicate pay for same order returns the **existing active** PaymentIntent when safe. Never create multiple active attempts unintentionally. | **APPROVED** |
| **P6-D7** | **Auto Stripe refund** for late success on `CANCELLED` / `PAYMENT_FAILED`. On refund failure or **uncertain** outcome → `RECONCILIATION_REQUIRED`. Never duplicate refunds. Stable Stripe idempotency keys; **reconcile uncertain outcomes before retry**. | **APPROVED** |
| **P6-D8** | **Attempt** to cancel active PaymentIntent when order is canceled. Handle races where payment succeeds before cancel is confirmed. **Do not assume** cancel succeeded until confirmed or reconciled. | **APPROVED** |
| **P6-D9** | **No** further payment attempt for an order in `PAYMENT_FAILED`. Customer must **checkout a new order**. | **APPROVED** |
| **P6-D10** | Refunds via **transactional outbox + background worker** (retries, idempotency, failure handling). | **APPROVED** |
| **P6-D11** | **No** reconciliation UI in Phase 6. Persist reconciliation state; structured logs + ops docs for manual investigation. | **APPROVED** |

### Cross-cutting implementation rules (mandatory)

1. **Never hold a database transaction open while waiting for Stripe network calls.** Pattern: short DB TX → commit → Stripe call → separate DB TX / webhook to record confirmed outcome.  
2. **Do not mark a refund `REFUNDED` merely because a refund API request was submitted.** Only confirmed Stripe refund status (or equivalent webhook) marks success.  
3. **Do not mark a PaymentIntent locally `CANCELED` merely because a cancel API request was submitted.** Use `CANCEL_REQUESTED` (or equivalent) until Stripe confirms or a success race is reconciled via P6-D3.  
4. **Preserve Phase 5 terminal order-state rules** (`CANCELLED` / `PAYMENT_FAILED` stay terminal unpaid; release + promo restore once) unless an explicitly documented and tested change is required. Phase 6 only **adds** payment-driven `PENDING_PAYMENT` → `PAID` (commit once) and late-success refund without reversing those terminals.  
5. Stripe **Test Mode only**. Never store card PAN/CVV. **Do not start Phase 7.**

Internal work packages only (WP-A…F). **No** Phase 6.1 / 6.2 as top-level phases.

---

## 1. Exact Phase 6 scope

| Source | Goal / exit |
|--------|-------------|
| `DEVELOPMENT_PLAN.md` §8 | Payment & Events — Stripe sandbox, webhooks, Kafka events, notifications. Exit: **payment + Kafka tests pass** |
| `TASK_TRACKER.md` | Stripe; PaymentIntent + webhook; Kafka + `processed_events`; Mailhog; tests; outbox (**required**) |

### In scope

1. Stripe Test Mode PaymentIntents + webhook signature verification  
2. Payment persistence + CUSTOMER ownership / ADMIN read APIs  
3. Success → `PAID` + inventory **commit once** (`inventory_committed`)  
4. Failure / expiry → unpaid terminal + **release + promo restore once** (Phase 5 flags)  
5. Late terminal success → record + **auto refund** via outbox worker (P6-D3/D7/D10)  
6. Order cancel → attempt PI cancel **after** local TX (P6-D8); success-vs-cancel races → refund path  
7. Transactional outbox → Kafka; idempotent consumers; Mailhog notifications  
8. Docs + Phase 1–5 regression  

### Out of scope

| Item | Phase |
|------|--------|
| Stripe Live | Never |
| Card data storage | Forbidden |
| Re-pay on `PAYMENT_FAILED` order | Forbidden (P6-D9) — new checkout |
| Admin shipping transitions / reconciliation UI / full storefront | **7** |
| Intent creation inside checkout | Rejected (P6-D2) |

---

## 2. Phase 5 reuse and dependencies

| Capability | Note |
|------------|------|
| `PENDING_PAYMENT` + reserved stock | Unchanged checkout |
| Server-side totals/currency | Pay uses **order row only** |
| Cancel / admin `PAYMENT_FAILED` → release + promo restore once | Flags preserved |
| Inventory joins caller TX | Keep (P5-D9); Stripe calls **outside** that TX |
| `PAID` enum unused in P5 | Payment-module-only transition to `PAID` |
| stripe-java / spring-kafka | Reuse |

Phase 5 admin rule (**no mark-paid**) remains: only verified Stripe success (webhook path) may set `PAID`.

---

## 3. Payment architecture (Stripe I/O outside DB TX)

```text
CUSTOMER  POST /api/v1/orders/{orderId}/pay
  [TX] load owned PENDING_PAYMENT order; reject if PAYMENT_FAILED/CANCELLED/PAID
       if active PI row → return it (P6-D6); else reserve local payment row (PENDING_CREATE)
  COMMIT
  → Stripe createPaymentIntent (network; idempotency key stable per payment row)
  [TX] persist provider_ref, client_secret ref, status REQUIRES_ACTION | PENDING
  → Return clientSecret (no card data)

CUSTOMER  POST /orders/{id}/cancel   (Phase 5 path + P6-D8)
  [TX] CANCELLED + release + promo restore once; payment → CANCEL_REQUESTED if still open
       outbox optional: payment.cancel_requested
  COMMIT
  → Stripe cancelPaymentIntent (network; do NOT assume success)
  → Confirm via Stripe response and/or webhook; else leave CANCEL_REQUESTED / reconcile
  → If payment_intent.succeeded wins race → P6-D3/D7 refund (order stays CANCELLED)

Stripe  POST /api/v1/payments/webhook/stripe
  → Verify signature (no DB yet)
  [TX] claim processed_events UNIQUE; lock order/payment; apply §4–§6; write outbox
  COMMIT
  → Outbox publisher → Kafka (including refund.requested)

Refund worker (P6-D10)
  ← outbox/Kafka refund.requested
  [TX] claim work if REFUND_PENDING and not already submitted with key; set REFUND_SUBMITTED
  COMMIT
  → Stripe Refund (stable Idempotency-Key)
  → On definitive success (API confirmed status or refund webhook): [TX] REFUNDED
  → On definitive failure: [TX] RECONCILIATION_REQUIRED (+ structured logs)
  → On uncertain (timeout / ambiguous): [TX] RECONCILIATION_REQUIRED or RECONCILE_BEFORE_RETRY;
       **do not** blindly retry until Stripe state is fetched/reconciled (P6-D7)
```

### Module layout

```text
payment/     api, domain, provider, service (pay, webhook, refund worker), dto, repository
shared/outbox/
notification/
```

---

## 4. Explicit state machines

### 4.1 Order (Phase 6 money path — Phase 5 terminals preserved)

```text
PENDING_PAYMENT
  ├─ customer cancel ──► CANCELLED (+ release + promo restore once)  [Phase 5]
  ├─ admin / payment failure path ──► PAYMENT_FAILED (+ release + restore once)  [Phase 5]
  └─ verified payment_intent.succeeded ──► PAID (+ inventory_committed once)  [Phase 6 only]

PAID ── (no shipping APIs in Phase 6 — P6-D5)

Terminal unpaid CANCELLED | PAYMENT_FAILED:
  - Never transition to PAID via late webhook
  - Never inventory commit
  - Never reverse terminal via duplicate failure webhooks
  - Late success → refund workflow only (order status unchanged)
```

**P6-D9:** `POST …/pay` allowed **only** when order is `PENDING_PAYMENT`.  
`PAYMENT_FAILED` / `CANCELLED` / `PAID` → reject (4xx). New purchase ⇒ new checkout.

### 4.2 Payment

```text
PENDING_CREATE → (Stripe create OK) → REQUIRES_ACTION | PENDING   // active
                → (Stripe create fail) → CREATE_FAILED

REQUIRES_ACTION | PENDING
  ├─ succeeded + order PENDING_PAYMENT ──► SUCCEEDED
  ├─ payment_failed ───────────────────► FAILED
  ├─ cancel requested locally ─────────► CANCEL_REQUESTED
  │     ├─ Stripe/webhook confirms canceled ──► CANCELED
  │     └─ succeeded wins race ──► SUCCEEDED_NEEDS_REFUND (order terminal unpaid)
  ├─ Stripe canceled / expired ────────► CANCELED
  └─ succeeded + order CANCELLED|PAYMENT_FAILED ──► SUCCEEDED_NEEDS_REFUND
```

**Active** (P6-D6 return existing): `REQUIRES_ACTION`, `PENDING`, `CANCEL_REQUESTED` (still may succeed until confirmed canceled — do not create a second PI).

### 4.3 Refund (P6-D7 / D10)

```text
(none) → insert refunds UNIQUE(payment_id, reason_code) → REFUND_PENDING
  → worker submits Stripe refund → REFUND_SUBMITTED  (not yet successful)
  → confirmed succeeded → REFUNDED  (provider_refund_ref UNIQUE)
  → confirmed failed OR uncertain without safe retry → RECONCILIATION_REQUIRED

Rules:
  - Never duplicate refunds (DB unique + stable Stripe idempotency key)
  - Uncertain → reconcile (retrieve Refund/PI from Stripe) before any retry
  - Submission ≠ success
```

### 4.4 Webhook events

```text
signature invalid → 4xx, not processed
signature OK → claim processed_events → apply → 200
duplicate event_id → 200 no-op
```

---

## 5. Late success + auto refund (P6-D3 / D7 / D10)

| Step | Action |
|------|--------|
| 1 | Verify signature; short TX: claim event, lock rows, amount/currency check |
| 2 | `PENDING_PAYMENT` → normal PAID + commit once |
| 3 | `PAID` → idempotent no-op |
| 4 | `CANCELLED` \| `PAYMENT_FAILED` → payment `SUCCEEDED_NEEDS_REFUND`; insert refund row; outbox `refund.requested`; **order unchanged** |
| 5 | COMMIT (no Stripe call inside this TX) |
| 6 | Worker: submit refund with stable idempotency key; persist `REFUND_SUBMITTED` |
| 7 | Confirm outcome → `REFUNDED` or `RECONCILIATION_REQUIRED` |
| 8 | Structured logs include orderId, paymentId, provider_ref, refundId, reason_code, stripe ids |

Ops (P6-D11): no UI; use DB state + logs + `docs` runbook section in completion report / SECURITY ops notes.

---

## 6. Success / failure / cancel races

### Success (order still `PENDING_PAYMENT`)

Short webhook TX: validate → `PAID` → commit inventory once → payment `SUCCEEDED` → outbox → commit. Stripe already happened; no outbound Stripe in this TX.

### Failure / expiry while `PENDING_PAYMENT`

→ `PAYMENT_FAILED` + Phase 5 compensation once; payment `FAILED`/`CANCELED` when Stripe confirms. **No re-pay on that order (P6-D9).**

### Cancel vs success race (P6-D8)

1. Local cancel TX completes first → order `CANCELLED`, payment `CANCEL_REQUESTED`, compensation done.  
2. Outbound cancel may fail, timeout, or lose to a concurrent capture.  
3. If webhook `succeeded` arrives → **refund path**, not PAID.  
4. If webhook `canceled` arrives → payment `CANCELED`.  
5. Never leave money captured without either PAID+commit (happy path) or recorded refund/reconciliation (orphan path).

---

## 7. PaymentIntent cancellation & expiration

| Trigger | Behavior |
|---------|----------|
| Customer cancel | After local TX: attempt Stripe cancel; local `CANCEL_REQUESTED` until confirmed |
| Stripe expiry / auto-cancel webhook | If still `PENDING_PAYMENT` → `PAYMENT_FAILED` + compensate once; else payment-only update |
| Pay on `PAYMENT_FAILED` | **Rejected** (P6-D9) |
| Duplicate pay on `PENDING_PAYMENT` with active PI | Return existing (P6-D6) |

---

## 8. Database (Flyway V8+)

- `orders.inventory_committed BOOLEAN NOT NULL DEFAULT FALSE`  
- `payments` — provider_ref UNIQUE, idempotency_key UNIQUE, statuses including `CANCEL_REQUESTED`, `SUCCEEDED_NEEDS_REFUND`, …; ≤1 active attempt per order  
- `refunds` — UNIQUE(payment_id, reason_code); provider_refund_ref UNIQUE NULL; idempotency_key UNIQUE; statuses `REFUND_PENDING` \| `REFUND_SUBMITTED` \| `REFUNDED` \| `RECONCILIATION_REQUIRED`  
- `outbox_events`, `processed_events`, `notification_logs`  

Concurrency: `FOR UPDATE` on order/payment inside short TXs only.

---

## 9. Kafka outbox & Mailhog (P6-D4 / D10)

Money-path and refund **booking** rows commit with outbox; publisher sends after commit. Refund worker and notification consumers are idempotent via `processed_events`. Mailhog for local SMTP. Core PAID/commit never delegated to Kafka consumers.

Topics (illustrative): `payment.succeeded`, `payment.failed`, `refund.requested`, `order.paid`.

---

## 10. Security

| Control | Requirement |
|---------|-------------|
| Webhook signature | Verify; reject invalid |
| Amount/currency | Order row only |
| Ownership | CUSTOMER own pay/read; ADMIN read; no forge-success API |
| Phase 5 admin | Still no mark-paid |
| Secrets | Env test keys only |
| Idempotency | Pay, webhook events, refund keys + reconcile-before-retry |
| No card storage | clientSecret / Elements only |
| No client authority | Redirect ≠ paid |

---

## 11. API sketch (sync to `API_DOCUMENTATION.md` at implement)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/orders/{orderId}/pay` | CUSTOMER | Only `PENDING_PAYMENT`; else 4xx (incl. `PAYMENT_FAILED`) |
| GET | `/api/v1/payments/{id}` | CUSTOMER\* / ADMIN | Ownership |
| GET | `/api/v1/orders/{orderId}/payment` | CUSTOMER\* | Current payment |
| POST | `/api/v1/payments/webhook/stripe` | Public + signature | |

Checkout / cancel / admin unpaid transitions remain as Phase 5 unless documented test-backed change.

---

## 12. Environment & local Stripe testing

`STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET` (test only). Stripe CLI forward webhooks. CI mocks `PaymentProvider` — no real credentials.

---

## 13. Testing strategy (minimum)

- Pay valid / invalid / non-`PENDING_PAYMENT` (incl. **reject `PAYMENT_FAILED`** — P6-D9)  
- Duplicate pay → same active intent  
- Amount/currency tamper rejected  
- Invalid signature; success; duplicate success; failure; expired/canceled  
- Cancel then late success → no PAID, no commit; **one** refund workflow; duplicate webhook no second refund  
- Cancel API vs success race  
- Refund: submit ≠ REFUNDED; uncertain → reconciliation; idempotent retry after reconcile  
- No Stripe call inside open DB TX (assert via test design / fakes)  
- Inventory commit/release once; promo consistency; TX rollback  
- CUSTOMER isolation; ADMIN cannot forge success  
- Outbox + consumer idempotency  
- Phase 1–5 regression  

---

## 14. Work packages (single Phase 6)

| WP | Work |
|----|------|
| A | Schema + domain/transitions (`inventory_committed`, payments, refunds, outbox, processed_events) |
| B | Pay API + provider + P6-D6/D9 (Stripe outside TX) |
| C | Webhooks + PAID/FAILED + P6-D3/D8 races |
| D | Outbox + Kafka + refund worker (P6-D7/D10) |
| E | Notifications + Mailhog |
| F | Docs (API, SECURITY, tracker, ops reconciliation notes) + `PHASE6_COMPLETION_REPORT` + suites → **STOP** |

---

## 15. Consistency vs project docs

| Doc | Consistency with P6-D1…D11 |
|-----|----------------------------|
| **DEVELOPMENT_PLAN.md** | Phase 6 = Stripe + webhooks + Kafka + notifications — **aligned**. Principles: TX boundaries, Kafka async not for money commit, idempotency — **aligned**. Outbox risk “hardening” → **required** (D4/D10). Critical-path sketch “Create Payment Intent” inside checkout → **overridden by P6-D2** (separate pay); same end state (intent + webhook → PAID). Shipping after PAID → **deferred P6-D5**. |
| **TASK_TRACKER.md** | Phase 6 checklist matches intent; will reword at implement: “intent on checkout” → **separate pay**; “optional outbox” → **required outbox + refund worker**. |
| **API_DOCUMENTATION.md** | Phase 5 checkout/cancel/admin unpaid-only — **preserved**. Phase 6 adds `/pay`, payment GETs, Stripe webhook; documents reject pay on `PAYMENT_FAILED` (D9); no admin mark-paid. |
| **SECURITY.md** (`docs/SECURITY.md`) | Phase 5: ownership, no mark-paid, compensate once — **preserved**. Phase 6 adds webhook signature, server amounts, refund idempotency, no card storage, no forge-success — extends without contradicting. |

**No remaining product decision forks** for Phase 6 money path.

---

## 16. Known limitations

- Test Mode only; not production payment certification.  
- No reconciliation UI / shipping admin / full Elements storefront (Phase 7).  
- Exactly-once at app + DB constraint level, not XA with Stripe.  
- Uncertain Stripe outcomes require ops using persisted `RECONCILIATION_REQUIRED` + logs.

---

## Hardening notes (post-implementation)

- `V9__payments_one_open_attempt_per_order.sql`: portable `active_lock_order_id` UNIQUE.  
- `db/migration_postgresql/V10__…`: PostgreSQL partial unique index (not loaded on H2).  
- Pay path locks **order then payment**; concurrent finalize OLFE → reload winner intent.  
- `RefundReconciliationService` for safe ops recovery without unrestricted SQL.

## Gate

**Phase 6 implementation + hardening complete.**  
**Do not start Phase 7** without explicit approval.
