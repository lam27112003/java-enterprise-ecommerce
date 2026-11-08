# API Documentation (Phase 5)

Base path: `/api/v1`  
Auth: Bearer JWT access token unless marked Public.

## Catalog — public

### `GET /categories`
Returns active category tree (`children` nested).

### `GET /categories/{id|slug}`
Active category detail.

### `GET /products`
Query params: `q`, `categoryId`, `minPrice`, `maxPrice`, `page`, `size`, `sort` (`name|priceCents|createdAt|sku`), `direction` (`asc|desc`).  
Returns only `ACTIVE` products (paged).

### `GET /products/{id|slug}`
Active product detail including images.

## Catalog — admin (`ROLE_ADMIN`)

### Categories
- `GET /admin/categories`
- `GET /admin/categories/{id}`
- `POST /admin/categories`
- `PUT /admin/categories/{id}`
- `DELETE /admin/categories/{id}` (soft delete; blocked if children/products exist)

### Products
- `GET /admin/products` (optional `status` filter)
- `GET /admin/products/{id}`
- `POST /admin/products`
- `PUT /admin/products/{id}`
- `DELETE /admin/products/{id}` (soft delete → `ARCHIVED`)
- `POST /admin/products/{id}/images` (`multipart/form-data` field `file`, optional `primary`)
- `DELETE /admin/products/{productId}/images/{imageId}`

## Inventory — admin (`ROLE_ADMIN`)

- `GET /admin/inventory` — paged stock list (`productId` filter optional)
- `GET /admin/inventory/{productId}` — single stock row
- `PATCH /admin/inventory/{productId}` — **delta-only** adjust  
  Body: `{ "delta": ±n, "reason": "..." }` (reason required; rejects result &lt; 0)  
  Returns adjustment audit fields (actor, before/after, timestamp).  
  No absolute quantity update API. Reserve/release/commit are **not** HTTP APIs.

## Cart — customer only (`ROLE_CUSTOMER` and **not** `ROLE_ADMIN`)

- `GET /cart`
- `POST /cart/items` — `{ productId, quantity }` (merge; refreshes price snapshot)
- `PATCH /cart/items/{itemId}` — qty only (keeps unit price snapshot)
- `DELETE /cart/items/{itemId}`
- `DELETE /cart` — clear  
- `POST /cart/promo` — `{ "code": "SAVE10" }` preview discount against current cart subtotal (does not mutate cart)

Ownership is always the authenticated user. Cart add does **not** reserve stock.

## Orders — customer only (`ROLE_CUSTOMER` and **not** `ROLE_ADMIN`)

### Checkout price snapshot behavior (P5-D2)

Checkout **always** loads current Catalog `priceCents`. Cart stored prices are ignored for order totals. Order lines persist immutable name, SKU, unit price, quantity, and line total. Tax is always `0` in Phase 5.

### `POST /orders/checkout`

Required header: `Idempotency-Key` (unique per customer).

Body (optional fields):

```json
{ "promotionCode": "SAVE10", "shippingAddressJson": "{\"line1\":\"...\"}" }
```

Creates order in `PENDING_PAYMENT`, reserves inventory, clears cart.  
Same key + same payload → original order. Same key + different payload → **409**.

Idempotency fingerprint: SHA-256 of canonical UTF-8  
`v1\npromotionCode=<UPPER TRIM>\nshippingAddressJson=<TRIM>\n`  
(see `CheckoutIdempotencyHasher`).

### Other customer order APIs

- `GET /orders` — own history (paged)
- `GET /orders/{id}` — own detail
- `POST /orders/{id}/cancel` — unpaid `PENDING_PAYMENT` only; releases stock + restores promo usage once
- `POST /orders/{id}/pay` — separate from checkout (P6-D2). Allowed only while `PENDING_PAYMENT`. `PAYMENT_FAILED`, `CANCELLED`, and `PAID` → **409** `ORDER_NOT_PAYABLE` (no re-pay; start a new checkout). Duplicate pay returns the existing active PaymentIntent. Response includes `clientSecret` for Stripe Elements. The server charges `orders.total_cents` and `orders.currency` only.
- `GET /orders/{id}/payment` — current payment for the authenticated customer. Does not include `clientSecret`.

## Payments

- `GET /payments/{id}` — customer (own payment) or admin. Does not include `clientSecret`.
- `POST /payments/webhook/stripe` — public. Requires a valid `Stripe-Signature`. Invalid signature → **400**. Amount or currency that does not match the order → **400** and the order is not marked `PAID`.

Verified `payment_intent.succeeded` on `PENDING_PAYMENT` sets `PAID` and commits reserved inventory once. The same event after `CANCELLED` or `PAYMENT_FAILED` does **not** set `PAID` and does **not** commit inventory; it records one refund workflow. Admin status APIs still cannot set `PAID`.

### Local Stripe CLI (Test Mode)

```bash
stripe listen --forward-to localhost:8080/api/v1/payments/webhook/stripe
```

Set `STRIPE_WEBHOOK_SECRET` to the CLI `whsec_…`. Use `STRIPE_MOCK=false` with test keys for real Test Mode; CI uses the mock provider. Mailhog UI: `http://localhost:8025`.

## Orders — admin (`ROLE_ADMIN`)

- `GET /admin/orders` — optional `status` filter
- `GET /admin/orders/{id}`
- `GET /admin/orders/{id}/status-history` — append-only history
- `PATCH /admin/orders/{id}/status` — body `{ "status", "trackingNumber?", "carrier?", "note?" }`  
  - Unpaid: `PENDING_PAYMENT` → `CANCELLED` | `PAYMENT_FAILED`  
  - Fulfillment: `PAID` → `PROCESSING` → `SHIPPED` → `DELIVERED` (`SHIPPED` requires tracking + carrier)  
  - Admin **cannot** set `PAID` (webhook / payment services only)

## Admin dashboard & users (Phase 7)

- `GET /admin/dashboard/summary?from=&to=` — KPIs; revenue is **payment-period** (`payments.succeeded_at` / `refunds.refunded_at`), per currency
- `GET /admin/users?q=&page=&size=` — list/search (roles read-only)
- `PATCH /admin/users/{id}/status` — `ACTIVE` | `DISABLED` | `SUSPENDED`; no self-disable; last ACTIVE ADMIN protected (no role-assignment API)

## Refund reconciliation — admin (Phase 7)

- `GET /admin/refunds` — optional status filter (e.g. `RECONCILIATION_REQUIRED`)
- `GET /admin/refunds/{id}` — detail + payment summary + audits
- `GET /admin/refunds/{id}/audits` — append-only audits
- `POST /admin/refunds/{id}/confirm-external` — `{ providerRefundRef, note? }` → **provider-verified** confirm (lookup key only; Stripe retrieve outside TX)
- `POST /admin/refunds/{id}/reopen-and-submit` — `{ note? }` → reopen with **same** idempotency key

## Promotions

### Admin CRUD (`ROLE_ADMIN`)

- `GET/POST /admin/promotions`
- `GET/PUT/DELETE /admin/promotions/{id}` (`DELETE` soft-deactivates)

Types: `PERCENT` (0–100) or `FIXED` (cents). Supports window, `usageLimit`, `minOrderAmountCents`, `active`.

### Customer validate

- `POST /promotions/validate` — `{ "code", "subtotalCents" }` → discount preview (`taxCents` always 0)

## Auth (Phase 2)

Unchanged: `/auth/register`, `/login`, `/refresh`, `/logout`, `/me`.

## Security notes

- Catalog reads are public; admin mutations require `ADMIN`.
- Cart/orders/promo-validate are CUSTOMER-only; ADMIN is denied.
- `X-Forwarded-For` / `X-Real-IP` trusted only when peer matches `TRUSTED_PROXIES`.
