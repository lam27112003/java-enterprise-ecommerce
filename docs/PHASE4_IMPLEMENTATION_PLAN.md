# Phase 4 Implementation Plan — Inventory & Shopping Cart

**Date:** 2026-11-04  
**Status:** Planning / readiness review only — **awaiting approval before implementation**  
**Sources:** `DEVELOPMENT_PLAN.md`, `TASK_TRACKER.md`, Phase 3 completion + API/security docs  

---

## 1. Exact Phase 4 scope (from approved plan)

| Source | Phase 4 goal / exit |
|--------|---------------------|
| `DEVELOPMENT_PLAN.md` §8 | **Inventory & Cart** — stock + cart + availability checks. Exit: **concurrency inventory tests pass** |
| `TASK_TRACKER.md` | Inventory entity + versioning; admin stock adjust; reserve/release/commit ops; cart APIs; availability on cart mutations; concurrent oversell tests |

### In scope

- Domain + Flyway for `inventory`, `carts`, `cart_items`
- Inventory service: adjust, **reserve**, **release**, **commit** (optimistic `@Version` + qty ≥ 0 constraints)
- Admin HTTP: list/adjust stock
- Customer HTTP: get/add/update/remove/clear cart; validate availability on mutations; cart totals
- Unit + Testcontainers integration + **parallel concurrency** tests
- Docs + tracker updates

### Explicitly out of scope (do not start)

| Item | Belongs to |
|------|------------|
| Orders, checkout, payment intents | Phase 5 / 6 |
| Promotions entity + `/cart/promo` redemption logic | Phase 5 |
| Kafka `inventory.low_stock` / `order.cancelled` consumers | Phase 6 |
| Storefront/admin React cart UI beyond current scaffold | Phase 7 |
| Phase 5+ features of any kind | — |

---

## 2. Business features

1. **Stock ledger per product** — available vs reserved quantities; never sell more than available.
2. **Admin stock management** — view inventory; adjust available quantity (increase/decrease with validation).
3. **Cart for authenticated customers** — add/update/remove/clear line items; snapshot unit price at add/update; recompute totals.
4. **Availability gates** — cart mutations only for `ACTIVE` catalog products with sufficient **sellable** stock (`quantity_available`).
5. **Reservation primitives** — reserve (available→reserved), release (reserved→available), commit (consume reserved) for Phase 5 checkout; exercised in Phase 4 via service/IT concurrency suites (not via checkout HTTP).

---

## 3. Backend modules and domain entities

Packages (per `DEVELOPMENT_PLAN.md` §3; align with existing `catalog` layout):

```text
com.ecommerce.platform.inventory/
  api/          AdminInventoryController
  domain/       Inventory
  dto/, mapper/, repository/, service/, exception/

com.ecommerce.platform.cart/
  api/          CartController
  domain/       Cart, CartItem
  dto/, mapper/, repository/, service/, exception/
```

| Entity | Responsibility |
|--------|----------------|
| `Inventory` | 1:1 with `products.id` (PK/FK); `quantityAvailable`, `quantityReserved`, `@Version` |
| `Cart` | One active cart per user (`user_id` unique); timestamps |
| `CartItem` | Line: `product_id`, `quantity`, `unitPriceCents` snapshot, currency; unique `(cart_id, product_id)` |

No JPA entities in API responses — DTOs only (existing principle).

---

## 4. Database tables and relationships

Proposed Flyway **`V5__inventory_cart.sql`** (name tentative; created only after implementation approval):

```text
products 1 ── 1 inventory
users    1 ── 0..1 carts  1 ── N cart_items N ── 1 products
```

| Table | Columns (plan-aligned) | Constraints / indexes |
|-------|------------------------|------------------------|
| `inventory` | `product_id` PK/FK → `products`, `quantity_available`, `quantity_reserved`, `version` | CHECK both qty ≥ 0; optional CHECK that available/reserved fit business bounds |
| `carts` | `id`, `user_id` UNIQUE NOT NULL (Phase 4 recommendation), `updated_at`, `created_at` | FK → `users`; index `user_id` |
| `cart_items` | `id`, `cart_id`, `product_id`, `quantity`, `unit_price_cents`, `currency` | UNIQUE `(cart_id, product_id)`; FK cascade from cart; qty > 0; price ≥ 0 |

**Money:** use **`BIGINT` cents** to match Phase 3 `products.price_cents` (not early-plan `NUMERIC`).

**Schema note:** plan allows nullable `session_id` for guest carts; Phase 4 API table lists cart as **CUSTOMER**. Default recommendation: **authenticated carts only** in V5; guest/session deferred unless approved (see §10).

---

## 5. API endpoints (Phase 4)

Base: `/api/v1`

### Inventory (ADMIN)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/inventory` | Paged/filtered stock list (product id/sku/name + qtys/version) |
| GET | `/admin/inventory/{productId}` | Single product stock (optional but useful; may fold into list) |
| PATCH | `/admin/inventory/{productId}` | Adjust available stock (delta or absolute — decide in impl; document in OpenAPI) |

### Cart (CUSTOMER; ADMIN may be allowed via same role rules as `/customer/**` if wired that way)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/cart` | Current user’s cart + line totals + grand total |
| POST | `/cart/items` | Add (or merge qty) `{ productId, quantity }` |
| PATCH | `/cart/items/{itemId}` | Update quantity |
| DELETE | `/cart/items/{itemId}` | Remove line |
| DELETE | `/cart` | Clear all items |

### Deferred from plan API list

| Endpoint | Reason |
|----------|--------|
| `POST /cart/promo` | Promotions module is Phase 5 |

### Not HTTP in Phase 4 (service API only)

- `reserve(productId, qty)`, `release(...)`, `commit(...)` — called by Order module later; covered by unit/IT concurrency tests now.

---

## 6. Frontend pages and user workflows

Per roadmap, **Phase 7** owns React storefront/admin UI. Phase 4 is **backend-first**.

| Workflow | Phase 4 | Phase 7 |
|----------|---------|---------|
| Admin adjusts stock | API + tests / OpenAPI | Admin Inventory page |
| Customer browses catalog → add to cart | Catalog already public (P3); cart API | Product detail “Add to cart”, Cart page, qty controls |
| Checkout | — | After Phase 5 APIs |

Frontend scaffold (`frontend/`) remains a Phase 1 placeholder; **no Phase 4 UI requirement** unless you explicitly expand scope.

---

## 7. Integration with Auth and Catalog

### Authentication / authorization

- Preserve JWT denylist, DB role reload, rate limits, trusted-proxy IP resolution.
- `/admin/inventory/**` → `ROLE_ADMIN` (existing `/api/v1/admin/**` matcher).
- `/cart/**` → authenticated `CUSTOMER` (and optionally `ADMIN`); require `SecurityConfig` matchers — **do not** weaken public catalog GETs.
- Cart ownership: resolve cart by authenticated `userId` only; never accept client-supplied user id.

### Catalog

- Cart lines reference existing `products` (UUID FK).
- Mutations: product must be **ACTIVE**, not soft-deleted; reject DRAFT/ARCHIVED.
- Price snapshot from `Product.priceCents` / `currency` at add or qty update (policy: refresh snapshot on update vs keep original — recommend **refresh on add/merge; keep snapshot on qty-only patch** unless approved otherwise).
- Soft-delete / archive of product: cart GET should mark lines unavailable or strip with clear error codes; do not allow qty increase for inactive products.
- Inventory is 1:1 with product; creating stock for unknown product → 404.

### Phase 3 readiness (no code changes found necessary)

| Dependency | Status |
|------------|--------|
| Products with UUID PK, `price_cents`, `currency`, `status` | Present |
| Soft delete / ARCHIVED semantics | Present |
| Users + RBAC (`CUSTOMER`/`ADMIN`) | Present |
| Public product read APIs | Present |
| Testcontainers Postgres/Redis patterns | Present |
| Inventory / cart tables | Absent (expected — Phase 4) |

**Blocking issues:** none identified. Phase 3 supplies catalog + auth foundations Phase 4 needs.

**Non-blocking gaps (handle inside Phase 4, not by changing Phase 3 behavior unless approved):**

- No inventory row today when products are created → Phase 4 must define provisioning (see §10).
- Catalog cache does not include stock → correct; stock reads stay on inventory module (optionally expose sellable qty on product DTO later; not required by tracker).

---

## 8. Transaction and concurrency requirements

From `DEVELOPMENT_PLAN.md` §2.2 / §5 inventory strategy:

| Operation | Semantics | Concurrency |
|-----------|-----------|-------------|
| Admin adjust | Change `quantity_available` under `@Transactional` | Optimistic `@Version`; conflict → retry or 409 |
| Reserve | `available -= n; reserved += n` if `available >= n` | Version check; insufficient → business 409 |
| Release | reverse reserve | Version check |
| Commit | `reserved -= n` (sold) | Version check; used by Phase 5 paid path |
| Cart mutate | Read stock + write cart items in one TX where needed | Availability re-check at mutation time |

DB: `CHECK (quantity_available >= 0 AND quantity_reserved >= 0)`.

**Mandatory test:** parallel threads cannot oversell (plan §9 scenario 4 precursor — reserve/commit race without order module).

Checkout atomicity with orders remains **Phase 5**.

---

## 9. Security requirements

- Admin-only stock mutation; customers cannot patch inventory.
- Cart endpoints authenticated; IDOR: item/cart access scoped to owner.
- No entity leakage; validate quantities (positive ints, sane max).
- Do not trust client-sent prices for authorization of stock — server snapshots price; stock checks server-side.
- Preserve Redis denylist fail-closed and rate-limit / trusted-proxy behavior.
- Optional: light rate limiting on cart mutations — **not** required by Phase 4 tracker; defer unless approved.
- OpenAPI: document new endpoints; keep prod docs gated as today.

---

## 10. Dependencies, risks, and decisions before implementation

### Dependencies

- Phase 2 auth + Phase 3 catalog (satisfied).
- Flyway V5 after V4.
- JPA optimistic locking support (available).
- Testcontainers Postgres for concurrency ITs (Redis not required for core stock races).

### Risks

| Risk | Mitigation |
|------|------------|
| Oversell under concurrency | `@Version` + CHECK + parallel ITs |
| Orphan products without inventory | Explicit create-on-product or lazy create-on-first-adjust policy |
| Cart holds stale price/stock | Snapshot + availability revalidation; clear error codes |
| Scope creep into checkout/promo | Strict exclusion list above |
| Guest cart ambiguity | Prefer authenticated-only until approved |

### Architectural decisions requiring your approval

| # | Decision | Recommendation |
|---|----------|----------------|
| D1 | Guest/session carts (`session_id`) in Phase 4? | **No** — authenticated `user_id` carts only; guest later if needed |
| D2 | Include `POST /cart/promo` in Phase 4? | **No** — Phase 5 with promotions |
| D3 | Inventory row lifecycle | **Auto-create `inventory(0,0)` when product is created** (catalog hook or application event) *or* create on first admin adjust; prefer auto-create on product create for cart safety |
| D4 | Expose reserve/release/commit over HTTP in Phase 4? | **No** — package-private/service API + tests; Order module calls in Phase 5 |
| D5 | Publish Kafka `inventory.low_stock`? | **No** in Phase 4 — stub/log threshold only optional; real events Phase 6 |
| D6 | Cart price snapshot on qty PATCH | Keep existing line snapshot; refresh only when adding/merging from catalog |
| D7 | Frontend cart UI in Phase 4? | **No** — Phase 7 |
| D8 | Admin adjust API shape | Prefer `{ "delta": ±n }` **or** `{ "quantityAvailable": n }` — pick one primary; document |

---

## 11. Unit and integration test strategy

| Layer | Focus |
|-------|--------|
| Unit | Inventory math (reserve/release/commit/adjust); insufficient stock; version conflict mapping; cart merge/totals; reject inactive products |
| `@DataJpaTest` / Postgres Testcontainers | Constraints; unique cart line; optimistic lock exception |
| `@SpringBootTest` + MockMvc/TestRestTemplate | Admin inventory authZ; cart CRUD authZ; IDOR negatives |
| Concurrency IT | N parallel reserves against stock=1 → exactly one success; no negative qty |
| Regression | Existing Surefire + Failsafe (auth, catalog, Redis security) must stay green |

**Do not mock** Postgres for inventory concurrency or constraint tests. Redis optional for Phase 4 unless cart caching is added (not planned).

---

## 12. Proposed implementation sequence (milestones)

| Milestone | Work | Acceptance criteria |
|-----------|------|---------------------|
| **M1 — Schema & domain** | Flyway V5; `Inventory`, `Cart`, `CartItem`; repositories | App starts; migrate clean on Postgres Testcontainers; constraints reject negative qty |
| **M2 — Inventory service** | Adjust + reserve/release/commit; admin controller | Admin GET/PATCH work; unauthorized → 401/403; unit tests for state transitions |
| **M3 — Inventory provisioning** | Per approved D3 (auto-create vs lazy) | Every ACTIVE product usable with cart/stock rules; no silent NPE on missing row |
| **M4 — Cart service & API** | Full cart endpoints; availability checks; totals | CUSTOMER happy path; inactive/insufficient → 4xx; clear cart works |
| **M5 — Concurrency & ITs** | Parallel oversell suite + Postgres ITs | Oversell impossible; Failsafe green with real PG |
| **M6 — Docs & gate** | API docs, SECURITY notes if any, `TASK_TRACKER`, completion report | Tracker accurate; full Surefire + Failsafe executed; **stop for Phase 5 approval** |

---

## 13. Phase 3 readiness verdict

**Ready to implement Phase 4 after plan approval.**  
No blocking defects found in Phase 3 that require code changes before Phase 4.  
Do not modify Phase 2/3 behavior except minimal, approved hooks (e.g. inventory row on product create) if D3 selects that approach.

---

## 14. Unresolved questions (need your approval)

1. Confirm **D1–D8** recommendations above (especially guest carts, `/cart/promo` deferral, inventory auto-create, no HTTP reserve, no Phase 4 frontend).
2. Admin adjust: **delta** vs **absolute quantity** (or both)?
3. Should **ADMIN** users share the customer cart API for manual testing, or customer-role only?
4. Low-stock threshold for future Kafka: define now as config stub, or ignore until Phase 6?

---

## Gate

**Do not implement Phase 4** until you approve this plan (and resolve §14).  
**Do not start Phase 5.**
