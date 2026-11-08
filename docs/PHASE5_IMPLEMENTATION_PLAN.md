# Phase 5 Implementation Plan — Orders & Promotions

**Date:** 2026-11-04  
**Status:** **Implemented** — see `docs/PHASE5_COMPLETION_REPORT.md`. Awaiting Phase 6 approval.  
**Sources:** `DEVELOPMENT_PLAN.md`, `TASK_TRACKER.md`, Phase 4 docs, API/SECURITY  
**Decision lock:** `docs/PHASE5_DECISION_LOCK.md`

---

## 1. Exact Phase 5 scope (from approved plan)

| Source | Phase 5 goal / exit |
|--------|---------------------|
| `DEVELOPMENT_PLAN.md` §8 | **Order & Promo** — checkout, order state machine, promotions. Exit: **order integration tests pass** |
| `TASK_TRACKER.md` | Promotion entity + admin CRUD + customer validate; checkout with line snapshots + promo; order history/detail/cancel; admin status updates; transactional checkout + reservation; order/promo ITs |

### In scope

- Promotions domain + Flyway + admin CRUD + customer validate + `POST /cart/promo` preview
- Checkout: catalog reprice, reserve, immutable snapshots, promo `usedCount++` in same TX, clear cart, required `Idempotency-Key`
- Orders start in **`PENDING_PAYMENT`** (P5-D1, P5-D6)
- Status machine: Phase 5 admin transitions per **P5-D11**; customer unpaid cancel per **P5-D5/D8**
- Cancel / `PAYMENT_FAILED`: inventory **release once** + promo **usage restore once** (**P5-D8/D10** + consistency note)
- Inventory TX join refactor (**P5-D9**)
- Concurrency + cancel/restore/duplicate-cancel tests
- Docs at implementation end: API, SECURITY, TASK_TRACKER, completion report

### Explicitly out of scope

| Item | Phase |
|------|--------|
| Stripe / PaymentIntents / payment APIs / webhooks / mark-paid | **6** (P5-D1, P5-D6) |
| Kafka, notifications | **6** |
| Frontend / storefront | **7** |
| Post-`PAID` cancel / refund | **6** (P5-D8) |
| Non-zero tax calculation | Future; Phase 5 `taxCents = 0` (P5-D7) |
| Transition to `PAID` or later statuses | **6** (P5-D11) |

### Critical path vs phase split

```text
Phase 5: Cart → (optional promo preview) → Checkout
         → Order PENDING_PAYMENT + Reserve + Promo usedCount++
         → Cancel or admin PAYMENT_FAILED
              → Release once + Restore usedCount once
         → (wait for Phase 6 payment)

Phase 6: Payment success → PAID + Commit reserved
```

---

## 2. Business features

1. Coupon promotions (percent/fixed, window, usage limit, min order, active).
2. Promo validate + cart promo preview (no inventory mutation).
3. Idempotent checkout with catalog reprice + reserve + usage increment.
4. Customer own-order history/detail; cancel eligible unpaid (release + restore once).
5. Admin order list; only `PENDING_PAYMENT` → `CANCELLED` | `PAYMENT_FAILED`.
6. Concurrent checkout / promo / idempotency / restore safety.

---

## 3. Order and checkout domain design

### Packages

```text
com.ecommerce.platform.order/
com.ecommerce.platform.promotion/
```

### Core entities

| Entity | Role |
|--------|------|
| `Order` | `orderNumber`, `userId`, `status`, money in cents, `taxCents=0`, `shippingAddressJson`, optional `promotionId`, `@Version` |
| `OrderItem` | Immutable snapshot: productId, sku, name, unitPriceCents, quantity, lineTotalCents |
| `OrderStatus` | Includes Phase 6-forward values; Phase 5 only reaches `PENDING_PAYMENT`, `CANCELLED`, `PAYMENT_FAILED` |
| `Promotion` | code, type, value, window, `usageLimit`, `usedCount`, min amount, active |
| `PromotionRedemption` | Written at checkout with `usedCount++`; marked restored / deleted on cancel path (impl detail) so restore is once |
| `CheckoutIdempotencyRecord` | UNIQUE `(user_id, idempotency_key)` + payload hash + `order_id` |

### Price snapshot behavior (P5-D2)

1. Cart may keep display snapshots (Phase 4 D6).
2. Checkout loads **current Catalog** `priceCents` / `currency`.
3. Cart prices are **never** final checkout prices.
4. Order lines store immutable name, SKU, unit price, qty, line total.
5. Later catalog changes do not alter existing orders. Document in API docs at implementation.

---

## 4. Promotion requirements

- Admin CRUD; customer validate + `/cart/promo` preview.
- **P5-D4:** Increment `usedCount` only in successful checkout TX; concurrent limit enforcement; rollback does not increment.
- **P5-D10:** On successful `PENDING_PAYMENT` cancel (and on admin `PAYMENT_FAILED` — see decision lock consistency note): restore `usedCount` exactly once, atomic with status change + inventory release; duplicate cancel must not restore again; ineligible/already-terminal orders must not restore.

---

## 5. Checkout workflow and status transitions

### Checkout (atomic TX — P5-D9)

```text
1. Require Idempotency-Key (P5-D3); resolve replay / 409
2. Load owned cart; reject empty
3. ACTIVE products; current catalog prices (P5-D2)
4. Validate promo; taxCents = 0; totals
5. Single DB transaction:
     - Reserve inventory (joins caller TX)
     - Insert order PENDING_PAYMENT + snapshots
     - usedCount++ + redemption (if promo)
     - Idempotency record
     - Clear cart
6. Commit; return order
```

### Phase 5 allowed transitions (P5-D11 + customer cancel)

```text
                    ┌── CUSTOMER cancel (own, eligible)
PENDING_PAYMENT ────┼── ADMIN → CANCELLED
                    └── ADMIN → PAYMENT_FAILED

CANCELLED        (terminal in Phase 5)
PAYMENT_FAILED   (terminal in Phase 5)

FORBIDDEN in Phase 5: any transition to PAID (or PROCESSING/SHIPPED/DELIVERED)
```

Enforce in the **order service layer** (P5-D11), not only controllers.

### Compensation on terminal unpaid exit (P5-D8, P5-D10)

In one service transaction, exactly once per order:

1. Transition `PENDING_PAYMENT` → `CANCELLED` or `PAYMENT_FAILED`
2. **Release** reserved inventory for all lines (once)
3. **Restore** promotion `usedCount` if a promo was applied (once)

Repeated requests after terminal state: no second release, no second restore.

---

## 6. Inventory (P5-D9)

- `reserve` / `release` / `commit` join caller transaction.
- No nested independently committing `TransactionTemplate`.
- Checkout / cancel / `PAYMENT_FAILED` compensation stay atomic with order + promo side effects.
- `commit` unused until Phase 6 `PAID`.

---

## 7. Cart validation

Preserve Phase 4: CUSTOMER-only; no reserve-on-add; ownership; checkout revalidates ACTIVE + stock; pricing from catalog at checkout.

---

## 8. PostgreSQL transaction boundaries

| Operation | Atomic unit |
|-----------|-------------|
| Checkout | reserve + order/items + usedCount/redemption + idempotency + clear cart |
| Customer cancel | status → CANCELLED + release once + restore usage once |
| Admin status | status → CANCELLED or PAYMENT_FAILED + same compensation |

---

## 9. Concurrency

- Oversell: parallel checkout, stock=1 → one winner.
- Promo usage limit under concurrent checkout.
- Idempotency races → one order.
- Duplicate cancel / concurrent cancel → single restore + single release.

---

## 10. Idempotency (P5-D3)

Required `Idempotency-Key`; UNIQUE per customer; same payload → original order; different payload → 409; no duplicate orders.

---

## 11. Payment (P5-D1 / P5-D6)

None. No Stripe, payment APIs, webhooks, mark-paid, or fake payment. Orders stay `PENDING_PAYMENT` until Phase 6. Admin `PAYMENT_FAILED` is a status transition only.

---

## 12. Security (P5-D5)

| Area | Rule |
|------|------|
| Checkout, list, cancel | CUSTOMER; own resources; eligible unpaid cancel only |
| `GET /orders/{id}` | Owner or ADMIN |
| `/admin/orders/**` | ADMIN; transitions only per P5-D11 |
| Preserve | Phase 2/3/4 auth, catalog, cart, inventory invariants |

---

## 13. API endpoints and migrations (implement only after coding approval)

Tentative `V6__orders_promotions.sql`: promotions, redemptions, orders, order_items (`tax_cents` default 0), checkout idempotency keys. No `payments` table.

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/promotions/validate` | CUSTOMER | Preview |
| CRUD | `/admin/promotions/**` | ADMIN | |
| POST | `/cart/promo` | CUSTOMER | Preview |
| POST | `/orders/checkout` | CUSTOMER | `Idempotency-Key` → `PENDING_PAYMENT` |
| GET | `/orders` | CUSTOMER | Own |
| GET | `/orders/{id}` | Owner/ADMIN | |
| POST | `/orders/{id}/cancel` | CUSTOMER owner | Unpaid eligible; compensate once |
| GET | `/admin/orders` | ADMIN | |
| PATCH | `/admin/orders/{id}/status` | ADMIN | Only → `CANCELLED` or `PAYMENT_FAILED` from `PENDING_PAYMENT` |

---

## 14. Testing strategy

| Focus | Requirement |
|-------|-------------|
| Unit | Promo rules; P5-D11 valid/invalid transitions; cancel eligibility; catalog vs cart price |
| Checkout IT | Snapshots; tax=0; reserve; usedCount++; idempotency replay/409 |
| Concurrency | Oversell; promo limit; idempotency |
| Cancel / restore IT | Release once; restore once; duplicate cancel no double restore; ineligible no restore |
| Admin status IT | Allowed transitions succeed; → `PAID` rejected; service-layer enforcement |
| Regression | Phases 2–4 Surefire + Failsafe after inventory TX refactor |

---

## 15. Consistency review (P5-D1…P5-D11)

| Check | Result |
|-------|--------|
| Decisions contradict each other? | **No** — D10 pairs with D4/D8; D11 pairs with D1/D6 (no PAID) |
| Scope still Orders & Promotions? | **Yes** |
| Stripe/Kafka/notifications/frontend in Phase 5? | **No** |
| Phase 2–4 invariants preserved? | **Yes** (plus required inventory TX join) |
| Phase 6 moved into Phase 5? | **No** — no payment, no commit-on-paid, no Kafka |

Clarification (documented in decision lock, not a change to D1–D9): admin `PAYMENT_FAILED` uses the same one-time inventory release + promo restore as cancel.

---

## 16. Locked decisions

See `docs/PHASE5_DECISION_LOCK.md`. **P5-D1 through P5-D11** are approved and locked.

---

## 17. Implementation sequence (only after explicit coding approval)

| Milestone | Work | Acceptance |
|-----------|------|------------|
| **M1** | Inventory TX join + Phase 4 regression | No nested independent inventory commits |
| **M2** | V6 schema | Postgres migrate clean |
| **M3** | Promotions + usage locking | Concurrent usage IT |
| **M4** | Checkout + catalog pricing + idempotency | Replay / 409 / atomic TX |
| **M5** | Cancel + admin transitions + restore/release once | D10/D11 tests green |
| **M6** | Full suites + docs/tracker/completion report | Stop for Phase 6 approval |

---

## Gate

**Planning and decision locking are complete.**  
**Implementation has not started.**  
**Do not write application code, migrations, or change runtime behavior until explicit implementation approval.**  
**Do not start Phase 6.**
