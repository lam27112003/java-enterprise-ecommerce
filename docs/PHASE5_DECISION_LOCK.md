# Phase 5 Decision Lock

**Date:** 2026-11-04  
**Status:** Decisions locked (P5-D1…P5-D11) — **implemented**; awaiting Phase 6 approval  
**Companion:** `docs/PHASE5_IMPLEMENTATION_PLAN.md`

Maps plan identifiers to final approved decisions. **Do not change P5-D1…P5-D9** without a new explicit approval.

| ID | Topic | Final decision |
|----|--------|----------------|
| **P5-D1** | Payment integration | **No** Stripe, PaymentIntents, payment APIs, or webhooks in Phase 5. Checkout creates orders in `PENDING_PAYMENT` only. Payment is Phase 6. |
| **P5-D2** | Checkout pricing | **Always** load **current catalog** unit price at checkout. Never use cart stored price as final. Persist immutable order-line snapshots (name, SKU, unit price, qty, line total). Document snapshot behavior in API/docs. |
| **P5-D3** | Checkout idempotency | **Require** `Idempotency-Key`. DB uniqueness scoped to customer (+ operation). Same key + same payload → original result. Same key + different payload → **HTTP 409**. Concurrent duplicates forbidden. |
| **P5-D4** | Promotion usage | Validate eligibility/limits at checkout. Increment `usedCount` **only** in the **successful checkout transaction**. Concurrent checkouts must not exceed usage limits. Rollback must not increment usage. |
| **P5-D5** | Order authorization | CUSTOMER: own orders only; cancel only eligible own orders. ADMIN: manage via explicit status-transition rules. CUSTOMER must not access admin order APIs. |
| **P5-D6** | Admin mark-paid | **Do not** implement admin mark-paid, manual payment confirmation, or fake payment simulation. Orders remain `PENDING_PAYMENT` until Phase 6 real payment. |
| **P5-D7** | Tax | `taxCents = 0` in Phase 5. Keep schema extensible. Document tax as not implemented. |
| **P5-D8** | Cancel after payment | CUSTOMER **cannot** cancel after `PAID`. Post-payment cancel/refund deferred to Phase 6. Eligible unpaid cancel releases reserved inventory **exactly once**. |
| **P5-D9** | Inventory TX join | Refactor `InventoryService` reserve/release/commit to **join caller transaction**. No nested independently committing `TransactionTemplate`. Checkout = atomic order + reserve + promo usage; any failure rolls back all. |
| **P5-D10** | Promotion usage restoration | Successful checkout increments `usedCount` in checkout TX. Successful cancel of `PENDING_PAYMENT` restores usage **exactly once**, **atomic** with order cancellation and inventory release. Repeated cancel must not restore again. Do not restore for already cancelled / ineligible orders. Unit + IT coverage required. |
| **P5-D11** | Admin status transitions | Phase 5 admin-allowed transitions only: `PENDING_PAYMENT` → `CANCELLED`, `PENDING_PAYMENT` → `PAYMENT_FAILED`. Both are **terminal** in Phase 5. **No** transition to `PAID`. No manual payment confirmation. Enforce in **service layer** (not controllers alone). Tests for valid and invalid transitions. |

### Additional locks

- Enforce valid order status transitions (no illegal jumps).
- Concurrency tests: checkout oversell, idempotency, inventory reservation, promotion usage limits, promo restore / duplicate cancel.
- Preserve Phase 2/3/4 security and business invariants.
- **No** Kafka, notifications, Stripe, or frontend in Phase 5.
- Update API docs, SECURITY, `TASK_TRACKER.md`, and Phase 5 completion report **at implementation end** (not before coding starts).

### Consistency notes (not new product scope)

1. **P5-D4 + P5-D10:** Increment on successful checkout; restore on successful unpaid cancel — paired lifecycle; each side exactly once per order.
2. **P5-D8 + P5-D10:** Unpaid cancel TX must atomically: set terminal status + release inventory once + restore promo usage once (when a promo was applied).
3. **P5-D11 + compensation:** Admin `PENDING_PAYMENT` → `PAYMENT_FAILED` is a terminal unpaid failure. It must apply the **same** one-time compensation as cancel: release reserved inventory once and restore promo usage once (if applicable), in the same service-layer transaction. This does not introduce payment simulation (P5-D6); it only frees reservation/usage when payment is marked failed.
4. **P5-D6 + P5-D11:** `PAYMENT_FAILED` is an admin status transition only — not a Stripe/webhook/mark-paid path.
5. Enum values `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED` may exist for forward compatibility; Phase 5 **must not** transition into `PAID` or beyond.

### Phase 5 vs Phase 6 (status / inventory / promo)

| Event | Phase 5 | Phase 6 |
|-------|---------|---------|
| Checkout | → `PENDING_PAYMENT` + **reserve** + `usedCount++` | — |
| Unpaid cancel / admin `CANCELLED` | → `CANCELLED` + **release** once + **restore usage** once | — |
| Admin `PAYMENT_FAILED` | → `PAYMENT_FAILED` + **release** once + **restore usage** once | — |
| Payment success | Not available | → `PAID` + **commit** reserved |
| Post-`PAID` cancel / refund | Not available | Deferred workflows |

### Remaining blockers before coding

**None.** Planning and decision locking (P5-D1…P5-D11) are complete.

Non-blocking implementation detail (choose during coding without changing locks):

- Exact payload fingerprint algorithm for P5-D3 (e.g. canonical JSON hash of checkout body).

### Gate

Phase 5 implementation is complete per `docs/PHASE5_COMPLETION_REPORT.md`.  
**Do not start Phase 6** until explicit approval.
