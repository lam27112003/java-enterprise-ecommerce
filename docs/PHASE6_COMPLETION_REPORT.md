# Phase 6 Completion Report — Payment, Events & Notifications

**Date:** 2026-11-05 (hardening review)  
**Status:** Complete + hardened — awaiting approval before Phase 7  
**Decisions:** P6-D1…D11 locked and implemented  
**Stripe:** Test Mode only (`FakePaymentProvider` in CI)

---

## Summary

Phase 6 adds Stripe PaymentIntents (separate `POST /orders/{id}/pay`), signature-verified webhooks, exactly-once inventory commit on `PAID`, late-success refund/reconciliation via transactional outbox + refund worker, Kafka domain topics, and Mailhog-oriented email notifications. Phase 5 terminal order rules are preserved. Shipping admin APIs and storefront UI were not started (Phase 7).

**Hardening (2026-11-05):** DB-enforced one open payment attempt per order, concurrent-pay safety, PostgreSQL 16 + Kafka Testcontainers ITs, controlled refund reconciliation API (no UI).

---

## What shipped

| Area | Detail |
|------|--------|
| Schema V8 | `orders.inventory_committed`, `payments`, `refunds`, `outbox_events`, `notification_logs` (reuses V1 `processed_events`) |
| Schema V9 | `payments.active_lock_order_id` UNIQUE (portable H2 + PostgreSQL) |
| Schema V10 (PG only) | Partial unique index `uq_payments_one_open_attempt_per_order` on open statuses |
| Pay API | `POST /api/v1/orders/{orderId}/pay` — server totals only; P6-D6 reuse active PI; P6-D9 reject `PAYMENT_FAILED` |
| Reads | `GET /payments/{id}`, `GET /orders/{id}/payment` (no `clientSecret` on GET) |
| Webhook | `POST /api/v1/payments/webhook/stripe` — public + signature; amount/currency checks |
| Success | `PENDING_PAYMENT` → `PAID` + commit once |
| Late success | Terminal unpaid unchanged; `SUCCEEDED_NEEDS_REFUND` + one `LATE_SUCCESS` refund |
| Cancel race | After-commit PI cancel; `CANCEL_REQUESTED` until confirmed; success race → refund |
| Outbox | Events written in money TX; dispatcher → Kafka (when enabled) or in-process |
| Refunds | `REFUND_PENDING` → `REFUND_SUBMITTED` → `REFUNDED` / `RECONCILIATION_REQUIRED` |
| Reconciliation | `RefundReconciliationService` — confirm external refund or reopen with **same** idempotency key |
| Notifications | Order paid / payment failed emails; failures do not reverse payments |
| Abstraction | `PaymentProvider` + `StripePaymentProvider` / `FakePaymentProvider` |

---

## Concurrency model (pay)

1. Short TX: `SELECT … FOR UPDATE` on **order**, then payments; insert at most one open row (`idempotency_key = pay:{orderId}`, `active_lock_order_id = order_id`).  
2. Commit; Stripe create **outside** TX (Stripe idempotency key = same key).  
3. Short TX: lock **order then payment** (consistent lock order); assign `provider_ref`. Concurrent optimistic lock losers reload the winner’s intent.  
4. PostgreSQL partial unique index rejects a second open-status row even if application logic regresses.

---

## State transitions (implemented)

**Order:** `PENDING_PAYMENT` → `PAID` (verified webhook only) | `CANCELLED` | `PAYMENT_FAILED`. Terminal unpaid never become `PAID` via late webhook.

**Payment:** `PENDING_CREATE` → `REQUIRES_ACTION`/`PENDING` → `SUCCEEDED` | `FAILED` | `CANCELED` | `CANCEL_REQUESTED` | `SUCCEEDED_NEEDS_REFUND` | `CREATE_FAILED`.

**Refund:** `REFUND_PENDING` → `REFUND_SUBMITTED` → `REFUNDED` | `RECONCILIATION_REQUIRED`. Submission is not success.

---

## Environment variables

| Variable | Purpose |
|----------|---------|
| `STRIPE_SECRET_KEY` | Test `sk_test_…` |
| `STRIPE_PUBLISHABLE_KEY` | Test `pk_test_…` |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` from Stripe CLI / Dashboard |
| `STRIPE_MOCK` | `true` for fake provider (tests default) |
| `MAIL_HOST` / `MAIL_PORT` | Mailhog `localhost:1025` |
| `MAIL_FROM` | From address |
| `KAFKA_BOOTSTRAP_SERVERS` | Broker |
| `OUTBOX_DISPATCH_DELAY_MS` | Outbox poll interval |

Placeholders only in `.env.example`. Never commit live keys.

---

## Local Stripe + Mailhog testing

1. Start Compose infra (Postgres, Redis, Kafka, Mailhog).  
2. Set Stripe **test** keys; `STRIPE_MOCK=false` for real Test Mode.  
3. Run backend (`local` profile). Flyway loads `db/migration` + `db/migration_postgresql`.  
4. Stripe CLI:  
   `stripe listen --forward-to localhost:8080/api/v1/payments/webhook/stripe`  
   Use the printed `whsec_…` as `STRIPE_WEBHOOK_SECRET`.  
5. Checkout → `POST /orders/{id}/pay` → confirm with test card in Elements (or Stripe test helpers).  
6. Inspect Mailhog UI `:8025` for confirmation mail; DB `notification_logs`, `outbox_events`, `refunds` for ops.

CI uses `app.stripe.mock=true` and does **not** need Stripe credentials.

---

## Recovery / reconciliation (P6-D11 — no UI)

When `refunds.status = RECONCILIATION_REQUIRED`:

### Safe procedure

1. Capture from logs/DB (read-only): `refunds.id`, `payment_id`, `order_id`, `idempotency_key`, `provider_refund_ref`, `reason_code`, related `payments.provider_ref`.  
2. In Stripe Test Dashboard/API, inspect the PaymentIntent and any Refund objects.  
3. **If Stripe already refunded:** call  
   `RefundReconciliationService.confirmExternalRefund(refundId, stripeRefundId)`  
   — sets `REFUNDED`, stores `provider_refund_ref`, **keeps the existing `idempotency_key`**. Do **not** insert another `refunds` row.  
4. **If Stripe has no refund and `provider_refund_ref` is null:** call  
   `RefundReconciliationService.reopenAndSubmit(refundId)`  
   — reopens to `REFUND_PENDING` and submits Stripe Refund with the **same** `idempotency_key`.  
5. **Never** invent a new idempotency key. **Never** `UPDATE refunds SET status='REFUNDED'` without a verified Stripe refund id. **Never** move terminal unpaid orders to `PAID`.

### Unsafe (forbidden)

- Unrestricted `UPDATE refunds …` / `UPDATE orders SET status='PAID' …` without matching Stripe evidence.  
- Creating a second refund row for the same `(payment_id, reason_code)`.  
- Retry storms without reconciling Stripe first.

There is no HTTP reconciliation UI in Phase 6; ops tooling should call the service methods above.

---

## Tests

| Suite | Result |
|-------|--------|
| Surefire (`mvn -B clean test` / verify) | **61** tests, **0** failures, **0** errors, **0** skipped |
| Failsafe (`mvn -B clean verify`) | **27** tests, **0** failures, **0** errors, **0** skipped |
| Frontend | Not touched |

New Failsafe coverage: `PaymentPostgresIT` (7), `PaymentOutboxKafkaIT` (1). Includes concurrent pay, duplicate webhook, outbox, refund uncertain + safe confirm/reopen, partial unique index, amount-mismatch rollback, Kafka broker publish.

Exactly-once is **application + DB constraints**, not distributed XA with Stripe.

---

## Known limitations

- Stripe Test Mode only — not production payment certification.  
- No Elements storefront / shipping admin / reconciliation UI (Phase 7).  
- H2 unit profile cannot load PostgreSQL partial unique index (`migration_postgresql` omitted); portable `active_lock_order_id` UNIQUE still applies.  
- Mailhog absent in unit profile: notification may log `FAILED` while payment still succeeds.  
- Concurrent pay under extreme load may surface optimistic-lock retries; losers converge on the single active intent.  
- Not production-ready solely because tests pass.

---

## Docs updated

- `TASK_TRACKER.md`  
- `DEVELOPMENT_PLAN.md`  
- `docs/API_DOCUMENTATION.md`  
- `docs/SECURITY.md`  
- `docs/PHASE6_IMPLEMENTATION_PLAN.md`  
- this report  

---

## Gate

**Phase 6 complete (including hardening). Do not start Phase 7 without explicit approval.**  
Not production-ready solely because tests pass.
