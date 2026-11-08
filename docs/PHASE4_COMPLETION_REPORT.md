# Phase 4 Completion Report — Inventory & Shopping Cart

**Date:** 2026-11-04  
**Status:** Complete — awaiting approval before Phase 5  

## Decision mapping (locked)

See `docs/PHASE4_DECISION_LOCK.md`. Summary:

| ID | Decision |
|----|----------|
| D1 | Authenticated carts only (no guest/`session_id`) |
| D2 | `/cart/promo` deferred to Phase 5 |
| D3 | Auto-create inventory (0 available, 0 reserved) on product create; V5 backfill |
| D4 | Reserve/release/commit internal only |
| D5 | Low-stock / Kafka deferred to Phase 6 |
| D6 | Qty PATCH keeps price snapshot; add/merge refreshes from catalog |
| D7 | No storefront UI |
| D8 | Delta-only admin adjust + required reason + audit |

Additional: CUSTOMER-only cart (ADMIN denied); no checkout/promotions/Kafka/Phase 5.

## Implementation summary

- Flyway `V5__inventory_cart.sql`: `inventory`, `inventory_adjustments`, `carts`, `cart_items`
- `InventoryService`: adjust (retrying optimistic lock), reserve/release/commit
- `AdminInventoryController`: GET list/detail, PATCH delta adjust
- `ProductService.create` → `ensureInventoryExists`
- `CartService` / `CartController`: CUSTOMER-only cart CRUD with availability checks
- Security: `/api/v1/cart/**` requires CUSTOMER; method security excludes ADMIN

## API endpoints

| Method | Path | Access |
|--------|------|--------|
| GET | `/api/v1/admin/inventory` | ADMIN |
| GET | `/api/v1/admin/inventory/{productId}` | ADMIN |
| PATCH | `/api/v1/admin/inventory/{productId}` | ADMIN (delta + reason) |
| GET/POST/PATCH/DELETE | `/api/v1/cart/**` | CUSTOMER only |

## Database migrations

`V5__inventory_cart.sql` (+ inventory backfill for existing products).

## Security considerations

- Cart IDOR prevented via user-scoped cart load.
- ADMIN cannot call cart APIs.
- No absolute stock set API; negative adjust rejected.
- Optimistic concurrency → 409 `INVENTORY_CONFLICT`.

## Test results (actual)

```text
mvn -B clean test
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn -B test-compile failsafe:integration-test failsafe:verify
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Failsafe includes: `InventoryConcurrencyIT` (3), `InventoryPostgresIT` (1), prior catalog/auth Redis/Postgres ITs.

Covered: concurrent final-unit reserve (exactly one winner), concurrent adjusts, TX rollback restores stock, cart authZ/ownership, inactive product, adjustment audit.

## Known limitations

- Cart lines can still over-subscribe available stock across users until Phase 5 checkout reserve (by design: no reserve-on-add).
- No low-stock notifications / Kafka.
- No guest carts; no frontend cart UI.
- Checkout / promotions are Phase 5.

## Gate

**Do not start Phase 5** until explicit approval.
