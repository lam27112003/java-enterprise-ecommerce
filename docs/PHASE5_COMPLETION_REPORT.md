# Phase 5 Completion Report — Orders & Promotions

**Date:** 2026-11-04  
**Status:** Complete — awaiting approval before Phase 6  

## Implemented features

- Checkout from authenticated customer cart with catalog reprice (P5-D2)
- Required `Idempotency-Key` with DB uniqueness and payload fingerprint (P5-D3)
- Inventory reserve/release join caller transaction (P5-D9); no nested commit on reserve path
- Promotions CRUD (admin), validate + cart promo preview, usage increment/restore (P5-D4/D10)
- Customer order list/detail/cancel; admin list/detail/status (P5-D5/D11)
- Orders remain `PENDING_PAYMENT` until Phase 6 (P5-D1/D6); `taxCents = 0` (P5-D7)
- Compensation on cancel / `PAYMENT_FAILED`: release inventory once + restore promo once

## API endpoints

| Method | Path | Access |
|--------|------|--------|
| POST | `/api/v1/orders/checkout` | CUSTOMER (header `Idempotency-Key` required) |
| GET | `/api/v1/orders` | CUSTOMER (own) |
| GET | `/api/v1/orders/{id}` | CUSTOMER (own) |
| POST | `/api/v1/orders/{id}/cancel` | CUSTOMER (own, unpaid) |
| GET | `/api/v1/admin/orders` | ADMIN |
| GET | `/api/v1/admin/orders/{id}` | ADMIN |
| PATCH | `/api/v1/admin/orders/{id}/status` | ADMIN (`CANCELLED` \| `PAYMENT_FAILED` only) |
| CRUD | `/api/v1/admin/promotions/**` | ADMIN |
| POST | `/api/v1/promotions/validate` | CUSTOMER |
| POST | `/api/v1/cart/promo` | CUSTOMER |

### Example checkout

```http
POST /api/v1/orders/checkout
Authorization: Bearer <access>
Idempotency-Key: checkout-2026-11-04-a1
Content-Type: application/json

{ "promotionCode": "SAVE10", "shippingAddressJson": "{\"city\":\"HN\"}" }
```

Response (201): order with `status=PENDING_PAYMENT`, `taxCents=0`, immutable `items[]` snapshots from **current catalog** prices.

## Database migrations

| Migration | Contents |
|-----------|----------|
| `V6__orders_foundation.sql` | `orders`, `order_items`, `checkout_idempotency_keys` |
| `V7__promotions_and_order_compensation.sql` | `promotions`, `promotion_redemptions`, order `inventory_released` / `promotion_usage_restored`, FK `orders.promotion_id` |

## Transaction design

Checkout (single TX via `TransactionTemplate`): validate cart → catalog prices → promo → reserve lines → persist order/items → redeem promo → idempotency row → clear cart. Failure rolls back all.

Cancel / admin `PAYMENT_FAILED`: status transition + release once + restore usage once (flags + redemption.restored).

## Idempotency fingerprint (P5-D3)

See `CheckoutIdempotencyHasher`:

1. Normalize `promotionCode` (trim, upper) and `shippingAddressJson` (trim).
2. Canonical UTF-8 text: `v1\npromotionCode=...\nshippingAddressJson=...\n`
3. SHA-256 → lowercase hex.
4. UNIQUE `(user_id, idempotency_key)`; hash mismatch → HTTP 409.

## Inventory

`InventoryService.reserve/release/commit` are `@Transactional` (REQUIRED) and join the caller. Admin `adjust` retains retrying `TransactionTemplate` (standalone).

## Promotion usage

- Increment + `promotion_redemptions` row in checkout TX.
- Restore: `redemption.markRestoredOnce()` + `usedCount--` + order `promotion_usage_restored`.
- Optimistic `@Version` on promotions for concurrent limit races.

## Status transitions (P5-D11)

`PENDING_PAYMENT` → `CANCELLED` | `PAYMENT_FAILED` only. No `PAID`. Service-layer enforced via `OrderStatusTransitions`.

## Security review

- CUSTOMER-only cart/orders/promotions validate; ADMIN excluded via method security + matchers.
- Order ownership on customer APIs; admin routes under `/admin/**`.
- No client price/discount trust; catalog + server promo math.
- Idempotency scoped per user; conflict on payload mismatch.
- Compensation flags prevent double release/restore.
- Phase 2 JWT/denylist/rate-limit/trusted-proxy unchanged.

## Test results (re-run on final code)

```text
mvn -B clean test
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn -B failsafe:integration-test failsafe:verify
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Includes `OrderCheckoutIntegrationTest`, `OrderCheckoutConcurrencyIT` (final-unit + promo limit), inventory concurrency, Postgres/Redis regression ITs.

## Known limitations / Phase 6 deferrals

- No Stripe / PaymentIntents / webhooks / mark-paid
- No Kafka / notifications / frontend
- No post-`PAID` cancel/refund
- Tax always 0
- Orders stay `PENDING_PAYMENT` until Phase 6 payment commits reserved stock

## Gate

**Phase 5 complete.** Do not start Phase 6 until explicit approval.
