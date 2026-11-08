# Phase 4 Decision Lock

Approved before implementation. Maps plan identifiers to final decisions.

| ID | Plan topic | Final decision |
|----|------------|----------------|
| **D1** | Guest/session carts | **No** — authenticated `user_id` only; no `session_id` |
| **D2** | `POST /cart/promo` | **Deferred to Phase 5** |
| **D3** | Inventory row lifecycle | **Auto-create** on product create with available=0, reserved=0; backfill existing products in V5 |
| **D4** | HTTP reserve/release/commit | **Internal services only** — no public HTTP in Phase 4 |
| **D5** | Kafka `inventory.low_stock` | **Deferred to Phase 6** — no notification infra in Phase 4 |
| **D6** | Price snapshot on qty PATCH | **Keep line snapshot** on qty-only update; refresh from catalog on add/merge |
| **D7** | Frontend cart UI | **No** — Phase 7 |
| **D8** | Admin adjust shape | **Delta-only** (`delta` ±n + required `reason`); no absolute quantity API; audit actor/timestamp/amount; optimistic lock |

### Additional locks

- Cart auth: **CUSTOMER only**; **ADMIN denied** even if also CUSTOMER; ownership on all ops.
- Cart does **not** reserve stock on add.
- Scope: no checkout, promotions, Kafka, storefront; no Phase 5.

### Contradictions check

- Plan API note that ADMIN may use cart → **overridden** by cart auth lock (CUSTOMER only).
- Plan D8 “delta or absolute” → **overridden** by delta-only + audit.
- Early schema `session_id` → **omitted** (D1).
- No other blocking contradictions with modular monolith / Boot 3.5 / cents money / existing SecurityConfig patterns.

### Implementation sequence

1. V5 schema + domain + audit  
2. Inventory service (adjust/reserve/release/commit) + admin API  
3. Product-create inventory hook  
4. Cart service + CUSTOMER-only API  
5. Unit + concurrency + Postgres ITs  
6. Full Surefire/Failsafe + docs/tracker + completion report  
