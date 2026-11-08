# Phase 7 Implementation Plan — Administration & Frontend

**Date:** 2026-11-05 (final planning review — revenue timestamps, admin concurrency, Stripe verify)  
**Workflow revision:** 2026-11-05 — continuous WP-D…G after WP-C (see §10.1)  
**Status:** **Complete (local)** — see `docs/PHASE7_FINAL_COMPLETION_REPORT.md`; Phase 8 not started  
**Prior phase:** Phase 6 hardened (`docs/PHASE6_COMPLETION_REPORT.md`, 2026-11-05)  
**Exit criterion (DEVELOPMENT_PLAN):** E2E happy path locally (storefront + admin + pay in Stripe Test Mode) + full §15 acceptance; final report `docs/PHASE7_FINAL_COMPLETION_REPORT.md`  
**Stripe Java SDK in repo:** `stripe-java` **34.0.0** (`backend/pom.xml`)

---

## 0. Scope statement

Phase 7 is **one major phase**. Internal work packages (WP-A…G) are allowed. Do **not** create Phase 7.1 / 7.2 as top-level roadmap phases.

### In scope

| Area | Intent |
|------|--------|
| **A. Customer storefront** | React SPA: browse, auth, cart, checkout, orders |
| **B. Stripe Elements** | Confirm PaymentIntents against Phase 6 backend (Test Mode) |
| **C. Admin dashboard** | Catalog, inventory, orders, promotions, KPIs, user **status** management |
| **D. Shipping management** | Order fulfillment fields + status history (no shipments domain) |
| **E. Refund reconciliation** | ADMIN UI + **provider-verified** confirm + append-only audit |
| **F. Compose / Docker** | Wire existing frontend Dockerfile into full-stack local run |
| **G. Tests & docs** | FE tests, Playwright E2E, API/SECURITY/tracker updates |

### Out of scope

| Item | Notes |
|------|--------|
| Stripe Live Mode / production deploy | Forbidden |
| AWS / Kubernetes (Phases 9–10) | Later |
| Kafka UI / observability polish (Phase 8) | Later |
| Changing Phase 1–6 money-path invariants | Preserve P6-D1…D11 |
| Treating frontend redirect / Elements success as payment proof | Webhook remains authoritative |
| Admin forge-`PAID` / reverse payment status | Forbidden |
| New refund rows or new Stripe idempotency keys on retry | Forbidden |
| Full `shipments` / multi-parcel domain | Rejected (P7-D1-A) |
| Address book | Rejected (P7-D3-A) |
| Role assignment / role-change APIs | Rejected (P7-D5) |
| Post-`PAID` cancellation, returns, restocking | Rejected (P7-D7-A) |
| ADMIN shopping via cart/checkout/pay | Rejected (P7-D2-A) |
| Accepting admin-typed Stripe Refund ID as sole proof of success | Forbidden (P7-D10) |

---

## 1. Decisions (P7-D1…D15)

| ID | Decision | Locked choice |
|----|----------|---------------|
| **P7-D1** | Shipping model | **A** — Extend order fulfillment fields; no shipments domain |
| **P7-D2** | Admin shopping | **A** — ADMIN remains blocked from cart, checkout, payment APIs |
| **P7-D3** | Profile / addresses | **A** — Existing `GET /auth/me` + checkout `shipping_address_json`; no address book |
| **P7-D4** | Refund audit | **B** — Separate append-only `refund_reconciliation_audits` |
| **P7-D5** | Admin user management | List, search, pagination, **account status only**; **no** role assignment APIs |
| **P7-D6** | Stripe publishable key | **A** — `VITE_STRIPE_PUBLISHABLE_KEY` only (no public config API required) |
| **P7-D7** | Post-`PAID` cancel / returns | **A** — Out of scope |
| **P7-D8** | FE server state | **A** — Lightweight fetch hooks; no Redux / TanStack Query |
| **P7-D9** | E2E | **B** — Playwright for critical customer + admin flows; local first; CI if stable/practical |
| **P7-D10** | Refund confirm proof | **NEW** — Backend **must** retrieve and verify refund via Stripe/`PaymentProvider` before `REFUNDED`; admin-supplied Refund ID is a lookup key only, never sufficient proof |
| **P7-D11** | Token storage | **Lock on approval** — access token in **memory**; refresh token in **sessionStorage** |
| **P7-D12** | Low-stock threshold | **Lock on approval** — default **5** (`app.inventory.low-stock-threshold`) |
| **P7-D13** | Order status history scope | **Lock on approval** — record **admin and system/payment** transitions |
| **P7-D14** | Product KPI counts | **Lock on approval** — `totalProducts` = all rows; separate `activeProducts` |
| **P7-D15** | Playwright CI | **Lock on approval** — enable in CI **only if** flake budget met; else local/`npm run test:e2e` |

P7-D1…D10 stay **unchanged**. Approving this plan also locks P7-D11…D15 unless explicitly overridden.

---

## 2. Inspection summary (docs + code)

### Documents read

`DEVELOPMENT_PLAN.md`, `TASK_TRACKER.md`, `README.md`, `docs/PHASE6_COMPLETION_REPORT.md`, `docs/PHASE6_IMPLEMENTATION_PLAN.md`, `docs/PHASE5_COMPLETION_REPORT.md`, `docs/API_DOCUMENTATION.md`, `docs/SECURITY.md`, prior `docs/PHASE7_IMPLEMENTATION_PLAN.md`.

### Code / infra verified (planning revision)

| Area | Finding |
|------|---------|
| Frontend | Scaffold: Vite 6, React 18.3, TS, Tailwind 3.4, Vitest; `react-router-dom` declared unused; Dockerfile + nginx exist |
| Auth refresh | **JSON body**, not cookies: `POST /api/v1/auth/refresh` with `RefreshRequest { refreshToken }`; response `AuthTokensResponse` includes new `accessToken` + rotated `refreshToken` |
| Logout | `POST /api/v1/auth/logout` with optional body `refreshToken`; Bearer access for `jti` denylist |
| Catalog / cart / checkout / pay | Customer APIs complete for money path |
| Admin CRUD | Categories, products, inventory (delta+reason), promotions, unpaid-only order cancel/fail |
| Order transitions | `OrderStatusTransitions` allows admin only `PENDING_PAYMENT → CANCELLED\|PAYMENT_FAILED`; forbids admin `PAID` |
| Shipping | No shipments table; `orders.shipping_address_json` only |
| Refunds | `RefundReconciliationService.confirmExternalRefund` today **trusts** admin-provided `providerRefundRef` without Stripe retrieve — **must harden in Phase 7 (P7-D10)** |
| Payment / refund timestamps | **No** `paid_at` / `succeeded_at` / `refunded_at`. Only `created_at` / `updated_at`. Order `created_at` = checkout time; refund `created_at` = booking time — **not** completion time |
| PaymentProvider | Has `createRefund`, `retrieveIntent`; **no** `retrieveRefund` yet — add for verification |
| Stripe SDK | `stripe-java` 34.0.0; refunds created with `RefundCreateParams.setPaymentIntent`; webhook already reads `refund.getPaymentIntent()` |
| Users | No `@Version`; `UserRepository` has **no** `FOR UPDATE` / lock helpers — last-ADMIN guard needs DB serialization in Phase 7 |
| Flyway | Shared `db/migration` through **V9**; PG-only **V10** in `db/migration_postgresql`; H2 tests omit PG path |
| Low-stock threshold | **Not configured** today — introduce explicit app property for dashboard KPI |
| Compose | `frontend` under profile `app` already defined |

---

## 3. Reuse map

### Reuse as-is (frontend consumes)

- Auth: register / login / refresh / logout / me  
- Catalog public + admin CRUD  
- Inventory admin adjust  
- Cart + promo preview/validate  
- Checkout + idempotency key  
- Orders list/detail/cancel (unpaid only)  
- Pay + payment GET + webhook (backend)  
- Promotions admin CRUD  
- Platform info  

### Extend (backend)

- Fulfillment transitions + tracking columns + `order_status_history`  
- Harden `confirmExternalRefund` with provider retrieve/verify (P7-D10)  
- `PaymentProvider.retrieveRefund(...)` (+ Charge fallback; Fake + Stripe impl)  
- Append-only `refund_reconciliation_audits`  
- **V13** `payments.succeeded_at` / `refunds.refunded_at` (set on real completion; used for revenue KPIs)  
- Admin refund list/detail/confirm/reopen HTTP  
- Admin dashboard summary (payment-period KPIs)  
- Admin user list/search/status (no roles) with DB-serialized last-ADMIN guard  

### Build new (frontend)

- Routing, layouts, guards, fetch hooks  
- Storefront + Stripe Elements  
- Admin console including shipping + refunds + users  
- Playwright suite  

---

## 4. Architecture

### 4.1 Frontend layout

```text
frontend/
  src/
    app/           # router, providers, layouts (CustomerLayout, AdminLayout)
    shared/        # ui, hooks (useFetch), lib (api client, token storage, errors)
    features/
      auth/
      catalog/
      cart/
      checkout/    # Stripe Elements
      orders/
      admin/       # dashboard, catalog, inventory, orders/shipping, promos, refunds, users
    styles/
```

| Choice | Locked |
|--------|--------|
| React | **18.3** (scaffold) |
| Router | **react-router-dom 6.x** |
| Tailwind | **3.4** |
| State | Auth context + lightweight fetch hooks (**P7-D8-A**); no Redux / TanStack Query |
| Forms | Controlled inputs + small validators |
| Stripe FE | `@stripe/stripe-js` + `@stripe/react-stripe-js`; **`VITE_STRIPE_PUBLISHABLE_KEY` only** |
| E2E | **Playwright** (P7-D9-B) |

### 4.2 Authentication frontend flow (verified against backend)

Backend is **stateless JWT + opaque refresh in JSON body** (not HttpOnly cookie).

1. **Login / register** → `AuthTokensResponse`: `accessToken`, `refreshToken`, `expiresInSeconds`, roles, user fields.  
2. **Storage (recommended):** access token in **memory**; refresh token in **sessionStorage** (survives tab reload, cleared on browser session end). Document XSS risk; never `localStorage` for long-lived secrets if avoidable.  
3. **API client:** `Authorization: Bearer <access>`. On **401** (expired/revoked access), single-flight `POST /auth/refresh` with `{ refreshToken }` from storage; replace both tokens; retry original request once.  
4. **Refresh reuse / family revoke:** if refresh fails → clear tokens → redirect `/login`.  
5. **Logout:** `POST /auth/logout` with Bearer + body `{ refreshToken }` when available; clear client state.  
6. **Guards:** `CustomerRoute` requires role `CUSTOMER` (and not relying on ADMIN dual-shopping); `AdminRoute` requires `ADMIN`. ADMIN hitting cart/checkout gets backend 403 — FE should hide those nav items.  
7. **Role claims in JWT are not authoritative** (SECURITY.md): after login/refresh, prefer `GET /auth/me` for display; admin APIs still re-check DB roles.

### 4.3 Payment UI rules (unchanged money authority)

1. Checkout → order → `POST …/orders/{id}/pay` → `clientSecret` (Phase 6 idempotency preserved).  
2. Stripe Elements confirms PaymentIntent (Test Mode).  
3. UI shows processing; **poll** `GET …/payment` and/or order status.  
4. **Webhook** is sole authority for `PAID`.  
5. Never ship `STRIPE_SECRET_KEY` / `sk_*` in Vite env; reject bundling secrets in review checklist.  
6. Never persist raw PAN/CVV in app backend or FE storage.  
7. Client secret used only for Elements confirmation; treat as sensitive short-lived material.

### 4.4 Shipping model (P7-D1-A)

Extend **orders** only:

- Unlock admin transitions: `PAID → PROCESSING → SHIPPED → DELIVERED`.  
- Columns: `tracking_number`, `carrier`, `shipped_at`, `delivered_at` (nullable).  
- Append-only `order_status_history` on every successful status change (admin fulfillment + unpaid admin cancel/fail + payment-driven `PAID` / unpaid terminal — at minimum all **admin** fulfillment changes; recommend also recording payment/system transitions for audit completeness).  
- Customer order detail shows status + tracking when present.  
- **No** post-`PAID` cancel (P7-D7-A).  
- **No** admin forge-`PAID`, reverse to unpaid, or payment-status mutation.

### 4.5 Refund reconciliation (P7-D4-B + P7-D10)

#### Gap today

`RefundReconciliationService.confirmExternalRefund(refundId, providerRefundRef)` persists the admin-supplied ref and sets `REFUNDED` **without** calling Stripe. That is unsafe for an HTTP admin UI.

#### Required Phase 7 behavior (exact verification — stripe-java 34.0.0)

Admin-supplied `providerRefundRef` is a **lookup key only**. Never treat ID string shape or presence as ownership proof.

```text
Admin POST confirm-external { providerRefundRef, note? }
        │
        ├─ TX1 (short): lock local Refund; require eligible status; load Payment;
        │     capture expectedPaymentIntentId = Payment.provider_ref, amount, currency, refundId
        │     commit / end TX before Stripe I/O
        ├─ OUTSIDE DB TX: RetrievedRefund = paymentProvider.retrieveRefund(providerRefundRef)
        │     • Stripe: Refund.retrieve(id, RequestOptions with configured secret key)
        │     • timeout / 5xx / rate-limit / null deserialize → UNCERTAIN; no REFUNDED; audit
        ├─ Verify (all must pass; any failure → REJECTED; no REFUNDED):
        │     1. retrieved.id equals requested providerRefundRef (exact)
        │     2. status equals "succeeded" (case-insensitive); pending/failed/canceled ≠ success
        │     3. amount equals local Refund.amount_cents (Stripe amount is Long cents)
        │     4. currency equals local Refund.currency (normalize lower/upper)
        │     5. Association to expected PaymentIntent (see charge path below)
        │     6. livemode consistent with app Test Mode (see mode check below)
        ├─ TX2 (short): re-lock Refund by id; re-check still eligible and not already REFUNDED;
        │     set provider_refund_ref from verified id; mark REFUNDED; set refunded_at;
        │     never change idempotency_key; never insert a second refunds row
        └─ Append refund_reconciliation_audits (SUCCESS / REJECTED / UNCERTAIN)
```

**PaymentIntent association (never infer from Refund ID alone):**

| Stripe fields (`com.stripe.model.Refund`) | Rule |
|------------------------------------------|------|
| `getPaymentIntent()` returns non-blank String | Must equal local `Payment.provider_ref` (PI id, e.g. `pi_…`) |
| `getPaymentIntent()` null/blank **and** `getCharge()` non-blank | **Outside TX:** `Charge.retrieve(chargeId)`; require `charge.getPaymentIntent()` equals expected PI; if charge missing PI or retrieve fails uncertain/reject per error class |
| Both PI and Charge missing/blank | **Reject** — cannot prove ownership |
| Expanded objects instead of ids | Prefer `getId()` on expanded `PaymentIntent`/`Charge` if SDK returns objects; if unusable → reject/uncertain, do not guess |

**Test Mode consistency:**

- Application Phase 7 uses **Stripe Test Mode only**.
- Configured `app.stripe.secretKey` must be `sk_test_…` (reject `sk_live_…` at startup or on provider init — document in SECURITY).
- Prefer `Refund.getLivemode() == Boolean.FALSE` when the field is present; if `livemode` is null (mock/partial), rely on secret-key mode check and still require PI ownership.
- Publishable key in FE must be `pk_test_…` (document; do not ship live keys).

**Reopen-and-submit:** Phase 6 rules unchanged (same idempotency key; refuse if `provider_refund_ref` set); Stripe I/O in worker outside booking TX; append audit `REOPEN_AND_SUBMIT`.

**FakePaymentProvider:** scriptable `retrieveRefund` (match / wrong PI / charge-only path / amount mismatch / non-succeeded / livemode true / uncertain/timeout).

---

## 5. API contracts (planned)

Base `/api/v1`. Existing contracts unchanged unless noted.

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| PATCH | `/admin/orders/{id}/status` | ADMIN | Extend body: `status` + optional `trackingNumber`, `carrier`, `note`; allow fulfillment edges only |
| GET | `/admin/orders/{id}/status-history` | ADMIN | List `order_status_history` |
| GET | `/admin/dashboard/summary` | ADMIN | KPIs (§7) |
| GET | `/admin/users` | ADMIN | List/search/paginate; return id, email, name, status, roles (**read-only**), createdAt |
| PATCH | `/admin/users/{id}/status` | ADMIN | `ACTIVE` \| `DISABLED` \| `SUSPENDED` only; protections in §8 |
| GET | `/admin/refunds` | ADMIN | Filter by status (esp. `RECONCILIATION_REQUIRED`) |
| GET | `/admin/refunds/{id}` | ADMIN | Detail + payment/order summary + idempotency key + audits |
| POST | `/admin/refunds/{id}/confirm-external` | ADMIN | `{ providerRefundRef, note? }` → verified confirm |
| POST | `/admin/refunds/{id}/reopen-and-submit` | ADMIN | `{ note? }` → reopen + worker submit |
| GET | `/admin/refunds/{id}/audits` | ADMIN | Append-only audit list (or embedded in detail) |

**Explicitly not in Phase 7**

| Method | Path | Reason |
|--------|------|--------|
| PATCH | `/admin/users/{id}/roles` | P7-D5 |
| * | Address book APIs | P7-D3-A |
| GET | `/config/public` (Stripe key) | P7-D6-A uses Vite env |
| * | Post-PAID cancel / return / restock | P7-D7-A |

### FE ↔ BE mismatches

| Topic | Reality | FE implication |
|-------|---------|----------------|
| Admin shopping | ADMIN denied cart/orders/pay | Separate CUSTOMER account to shop |
| Refresh transport | JSON body refresh token | Not cookie-based SPA auth |
| Publishable key | Not on API | `VITE_STRIPE_PUBLISHABLE_KEY` |
| Shipping | Address JSON string | Structured checkout form → JSON string |
| confirm-external | Must verify via provider | UI may show “verifying with Stripe…”; uncertain ≠ success |

---

## 6. Database / Flyway

### Existing versions

| Location | Latest | Notes |
|----------|--------|-------|
| `classpath:db/migration` | **V9** | Portable (H2 + PG) |
| `classpath:db/migration_postgresql` | **V10** | PG-only partial unique on open payments |
| H2 / `application-test.yml` | Omits `migration_postgresql` | Keep this split |

### Planned portable migrations (implementation only after approval)

| Version | File (illustrative) | Content |
|---------|---------------------|---------|
| **V11** | `V11__order_fulfillment_and_status_history.sql` | Order tracking columns + `order_status_history` |
| **V12** | `V12__refund_reconciliation_audits.sql` | Append-only `refund_reconciliation_audits` |
| **V13** | `V13__payment_refund_completion_timestamps.sql` | `payments.succeeded_at`, `refunds.refunded_at` (+ indexes); optional denormalized `orders.paid_at` |

**Do not reuse V10** (reserved for PG-only partial unique). No Phase 7 PG-only script required unless advisory-lock helpers need PG-specific SQL (prefer portable JDBC + dialect branch, or native query only in Postgres profile).

#### V13 timestamp semantics (required — columns do **not** exist today)

| Column | Set when | Not set when |
|--------|----------|--------------|
| `payments.succeeded_at` | First transition to `SUCCEEDED` or `SUCCEEDED_NEEDS_REFUND` from verified payment success | Payment create / pending / failed; do not overwrite if already set |
| `refunds.refunded_at` | First transition to `REFUNDED` (worker success, webhook, or verified confirm-external) | Refund booking (`created_at`); reconciliation-required; reopen |
| `orders.paid_at` *(optional denormalized)* | When order becomes `PAID` via payment path | Prefer reporting from `payments.succeeded_at`; if added, set once with payment mark-paid |

Backfill for existing Phase 6 rows (implementation choice, document in migration notes):  
`succeeded_at = updated_at` where status in (`SUCCEEDED`,`SUCCEEDED_NEEDS_REFUND`);  
`refunded_at = updated_at` where status = `REFUNDED`;  
treat as approximate for historical data only.

### 6.1 Order fulfillment columns (`orders`)

| Column | Type | Notes |
|--------|------|-------|
| `tracking_number` | VARCHAR(64) NULL | Required when transitioning to `SHIPPED` (validation) |
| `carrier` | VARCHAR(64) NULL | Required when transitioning to `SHIPPED` |
| `shipped_at` | TIMESTAMPTZ NULL | Set on first transition to `SHIPPED` |
| `delivered_at` | TIMESTAMPTZ NULL | Set on transition to `DELIVERED` |

### 6.2 `order_status_history` (append-only)

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `order_id` | UUID NOT NULL FK → `orders(id)` | |
| `previous_status` | VARCHAR(30) NOT NULL | |
| `new_status` | VARCHAR(30) NOT NULL | |
| `actor_user_id` | UUID NULL FK → `users(id)` | NULL for system/payment transitions |
| `note` | VARCHAR(500) NULL | |
| `created_at` | TIMESTAMPTZ NOT NULL | |

Indexes: `(order_id, created_at)`, `(created_at)`.  
**No** UPDATE/DELETE admin APIs. JPA entity insert-only.

### 6.3 `refund_reconciliation_audits` (append-only)

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `refund_id` | UUID NOT NULL FK → `refunds(id)` | |
| `actor_user_id` | UUID NOT NULL FK → `users(id)` | ADMIN who invoked API |
| `action` | VARCHAR(40) NOT NULL | See enum below |
| `previous_status` | VARCHAR(40) NOT NULL | |
| `new_status` | VARCHAR(40) NOT NULL | May equal previous on failed verify |
| `provider_refund_ref` | VARCHAR(255) NULL | |
| `note` | VARCHAR(1000) NULL | |
| `created_at` | TIMESTAMPTZ NOT NULL | |

Indexes: `(refund_id, created_at)`, `(actor_user_id, created_at)`, `(action, created_at)`.

**Actions (illustrative enum):**  
`CONFIRM_EXTERNAL_SUCCESS`, `CONFIRM_EXTERNAL_REJECTED`, `CONFIRM_EXTERNAL_UNCERTAIN`, `REOPEN_AND_SUBMIT`, `REOPEN_REJECTED`.

No silent overwrite/delete via normal admin APIs. Optional DB trigger / revoke UPDATE privilege is nice-to-have; application layer + no repository update methods are required.

### Valid shipping / order transitions (admin fulfillment)

```text
PAID ──► PROCESSING ──► SHIPPED ──► DELIVERED
```

| From | To | Rules |
|------|----|-------|
| PAID | PROCESSING | ADMIN; optional note |
| PROCESSING | SHIPPED | ADMIN; **require** tracking_number + carrier; set `shipped_at` |
| SHIPPED | DELIVERED | ADMIN; set `delivered_at` |
| * | PAID | **Forbidden** for admin (payment webhook / payment services only) |
| PAID+ | CANCELLED / PAYMENT_FAILED | **Forbidden** (P7-D7) |
| PENDING_PAYMENT | CANCELLED / PAYMENT_FAILED | Existing unpaid admin path only |

Each successful transition inserts `order_status_history`.

---

## 7. Dashboard KPI definitions

All money values in **integer cents**. Multi-currency: **per-currency breakdown only** — never sum different currencies.

Default date window for time-bounded metrics: **UTC calendar month-to-date** (`from`/`to` optional ISO-8601; default `from` = start of current UTC month, `to` = now).

### 7.1 Payment-period vs order-creation-period (critical)

| Reporting mode | Time axis | Use |
|----------------|-----------|-----|
| **Payment-period (default for revenue KPIs)** | `payments.succeeded_at` / `refunds.refunded_at` | Gross / refunded / net revenue on the dashboard |
| **Order-creation-period (not used for revenue)** | `orders.created_at` | Order volume / ops lists only — **must not** be labeled as revenue |

**Inspection result:** `orders.created_at` is checkout/place-order time; `refunds.created_at` is refund **booking** time; `updated_at` is any mutation. None of these are reliable completion timestamps. Phase 7 **must** add V13 columns before shipping payment-period revenue.

### 7.2 KPI formulas

| KPI | Formula / filter |
|-----|------------------|
| **totalProducts** | `COUNT(*)` from `products` (all statuses) — **P7-D14** |
| **activeProducts** | `COUNT(*)` from `products` where `status = ACTIVE` |
| **lowStockProducts** | Inventory rows with `quantity_available <= :threshold` (default **5**, P7-D12); exclude soft-deleted products if applicable |
| **openOrders** | Orders with `status = PENDING_PAYMENT` |
| **ordersAwaitingFulfillment** | Orders with `status IN (PAID, PROCESSING)` |
| **refundsRequiringReconciliation** | Refunds with `status = RECONCILIATION_REQUIRED` |
| **revenueGrossCents** | **Payment-period:** `SUM(payments.amount_cents)` where `status IN (SUCCEEDED, SUCCEEDED_NEEDS_REFUND)` **and** `succeeded_at ∈ [from, to]`, grouped by `payments.currency`. (Equivalent: join paid orders on successful payment; prefer payment row as source of truth.) |
| **revenueRefundedCents** | **Payment-period:** `SUM(refunds.amount_cents)` where `status = REFUNDED` **and** `refunded_at ∈ [from, to]`, grouped by `refunds.currency` |
| **revenueNetCents** | Per currency: `revenueGrossCents − revenueRefundedCents` (may be negative if refunds fall in a different window than the original payment — return as-is) |

API response must label the window as **payment/refund completion period** (not order-created period). Optionally expose a separate non-revenue metric `ordersCreatedInWindow` on `orders.created_at` if useful — never mix into revenue fields.

**Do not invent:** FX conversion, profit, inventory valuation, Kafka lag, or revenue from `orders.created_at` / `refunds.created_at`.

---

## 8. Security considerations

| Control | Requirement |
|---------|-------------|
| RBAC | `/admin/**` ADMIN-only; cart/orders/pay CUSTOMER-only (P7-D2-A) |
| No forge PAID | Admin status API rejects `PAID` and payment reversals |
| Refund verify | P7-D10 — Stripe retrieve outside TX; mismatch → reject; uncertain → no `REFUNDED` |
| Refund audit | Append-only; actor_user_id required |
| User status | No role APIs; block self-disable; block last active ADMIN disable/suspend |
| Stripe secrets | Server only; FE publishable key via Vite |
| XSS / CSRF | React escaping; stateless JWT |
| Rate limit | Sensitive admin POSTs (confirm-external, reopen) should reuse or add admin rate limits |
| Concurrent recon | Pessimistic lock on refund row; second confirm loses / conflicts cleanly |

### 8.1 User management safety (P7-D5) + concurrency

`PATCH /admin/users/{id}/status` (no role-change API):

1. Target must exist; only `ACTIVE` / `DISABLED` / `SUSPENDED`.  
2. **Refuse** if `targetId == authenticatedAdminId` (cannot disable/suspend self).  
3. **Refuse** if applying `DISABLED`/`SUSPENDED` to an `ADMIN` would leave **zero** `ADMIN` + `ACTIVE` users.  
4. Registration remains CUSTOMER-only; no self-privilege escalation.  
5. Status change takes effect on the next authenticated request (existing DB role/status revalidation).

#### Concurrency-safe last-active-ADMIN (required)

**Inspection:** `User` has no `@Version`; `UserRepository` has no pessimistic locks. An application-level `COUNT(*)` without serialization allows two active ADMINs to disable each other concurrently and leave **zero** active ADMINs.

**Required strategy (PostgreSQL — authoritative for production/IT):**

1. Open a short transaction.  
2. Take a **transaction-scoped serialization lock** before any status mutation that can remove an ACTIVE ADMIN:
   - **Preferred:** `SELECT pg_advisory_xact_lock(:ADMIN_STATUS_LOCK_KEY)` with a fixed 64-bit key constant documented in code.  
   - **Acceptable alternative:** `SELECT u.id FROM users u JOIN user_roles ur … JOIN roles r ON r.code = 'ADMIN' WHERE u.status = 'ACTIVE' FOR UPDATE` (lock all currently active ADMIN rows), then proceed.  
3. Under that lock: reload target; enforce self-disable ban; recount ACTIVE ADMINs; if target is ADMIN and new status is not ACTIVE and count of *other* ACTIVE ADMINs is 0 → abort.  
4. Apply status update; commit (advisory lock releases with TX).

Optional secondary: add `@Version` on `User` for lost-update detection — **not sufficient alone** for the last-ADMIN invariant.

H2/unit profiles: may use a JVM monitor around the same service method for standalone tests; **must not** replace the Postgres IT below.

#### Required Postgres Testcontainers IT

- Seed **two** ACTIVE ADMIN accounts (A, B).  
- Fire **concurrent** `PATCH` requests: A disables B and B disables A (or both suspend).  
- After both complete: assert **≥ 1** user remains `ADMIN` + `ACTIVE`.  
- Also cover: sole ADMIN cannot disable self or self-suspend; sole ADMIN cannot disable themselves via another path.

### 8.2 Refund uncertain / timeout handling

| Provider outcome | Local refund status | Audit action | HTTP |
|------------------|---------------------|--------------|------|
| Verified success | → `REFUNDED` | `CONFIRM_EXTERNAL_SUCCESS` | 200 |
| Mismatch / not succeeded / wrong PI / amount | unchanged | `CONFIRM_EXTERNAL_REJECTED` | 409 |
| Timeout / network / uncertain API | unchanged | `CONFIRM_EXTERNAL_UNCERTAIN` | 503 or 409 with dedicated code | 

Never mark success on timeout. Admin may retry confirm later.

---

## 9. Frontend pages & routes

### Customer

| Route | Page |
|-------|------|
| `/` | Home |
| `/products` | Catalog |
| `/products/:idOrSlug` | Detail |
| `/categories/:slug` | Category |
| `/login`, `/register` | Auth |
| `/account` | Profile from `/auth/me` (no address book) |
| `/cart` | Cart + promo |
| `/checkout` | Review + shipping JSON + place order |
| `/orders` | History |
| `/orders/:id` | Detail, unpaid cancel, tracking display |
| `/orders/:id/pay` | Elements pay step |

### Admin (`/admin/*`)

| Route | Page |
|-------|------|
| `/admin` | Dashboard (KPIs §7) |
| `/admin/products`… | Products + images |
| `/admin/categories` | Categories |
| `/admin/inventory` | Stock + adjust (reason) |
| `/admin/orders`… | Orders + fulfillment actions + history |
| `/admin/promotions` | Promotions |
| `/admin/refunds`… | Reconciliation + audits |
| `/admin/users` | List/search/status (no roles UI) |

---

## 10. Work packages (single Phase 7)

### WP-A — Frontend foundation

| | |
|--|--|
| **Objective** | SPA shell on existing Vite scaffold |
| **Scope** | Router, layouts, API client (Bearer + **body refresh**), auth context, env (`VITE_STRIPE_PUBLISHABLE_KEY`), Tailwind tokens |
| **Depends on** | None |
| **Acceptance** | Login/refresh/logout round-trip; guards; Vitest smoke |
| **Tests** | API client refresh single-flight; guard tests |

### WP-B — Customer storefront

| | |
|--|--|
| **Objective** | Browse + cart + account without payment |
| **Scope** | Home, catalog, detail, categories, cart, promo, account (`/auth/me`) |
| **Depends on** | WP-A |
| **Acceptance** | Guest browse; CUSTOMER cart; ADMIN cannot use cart UI meaningfully (403) |
| **Tests** | RTL catalog/cart |

### WP-C — Checkout & Stripe Elements

| | |
|--|--|
| **Objective** | Test Mode purchase path |
| **Scope** | Checkout shipping JSON, idempotent checkout, pay page, Elements, polling, unpaid cancel |
| **Depends on** | WP-B + Phase 6 |
| **Acceptance** | Test card → webhook → UI `PAID`; duplicate pay safe; no secret in bundle |
| **Tests** | Stripe mocks; manual Stripe CLI checklist |

### WP-D — Admin dashboard & management

| | |
|--|--|
| **Objective** | Operate catalog/inventory/promos + KPIs + user status |
| **Scope** | Admin UIs; V13 timestamps on payment/refund completion paths; `GET /admin/dashboard/summary`; `GET/PATCH` users status with advisory/`FOR UPDATE` serialization |
| **Depends on** | WP-A |
| **Acceptance** | KPIs match §7 (**payment-period** revenue via `succeeded_at`/`refunded_at`); last-admin + self-disable blocked under concurrency; no roles API |
| **Tests** | Dashboard aggregation IT (payment-period fixtures); concurrent last-ADMIN Postgres IT; self-disable IT; FE admin guards |

### WP-E — Shipping management

| | |
|--|--|
| **Objective** | Fulfill `PAID` orders with history |
| **Scope** | V11 columns + `order_status_history`; transition rules; admin UI; customer tracking |
| **Depends on** | WP-D |
| **Acceptance** | Only valid edges; cannot forge `PAID`; history appended; tracking validated on SHIPPED |
| **Tests** | Transition unit + Postgres IT; invalid transition 409 |

### WP-F — Refund reconciliation

| | |
|--|--|
| **Objective** | Safe ADMIN recon UI + hardened service |
| **Scope** | V12 audits; V13 `refunded_at`; `retrieveRefund` (+ Charge fallback); full §4.5 verify; HTTP APIs; UI |
| **Depends on** | WP-D + Phase 6 service |
| **Acceptance** | Mismatched/unowned Refund ID rejected; Charge→PI path covered; livemode/test-key checks; timeout ≠ success; audit append-only; no new refund row/key |
| **Tests** | See §11 |

### WP-G — Integration, Playwright, docs

| | |
|--|--|
| **Objective** | Phase 7 exit |
| **Scope** | Compose full stack; Playwright customer + admin flows; CI job if stable; update API/SECURITY/README/tracker; `mvn verify` + `npm test`/`build` |
| **Depends on** | WP-C, WP-E, WP-F |
| **Acceptance** | Documented E2E green locally; regression suites green; no production claim |

**Recommended order:** A → B → C → D → E → F → G.  
Independent tasks may run in parallel when dependencies permit (e.g. some WP-D backend work alongside late WP-C polish), but **do not start WP-D UI/product delivery before WP-C implementation and verification are complete**. Preserve all approved business invariants.

### 10.1 Execution workflow (approval policy — revised 2026-11-05)

| Stage | Gate |
|-------|------|
| WP-A, WP-B | Completed and formally approved (historical) |
| WP-C | **Approved for implementation** — finish verification + completion report before WP-D |
| WP-D, WP-E, WP-F, WP-G | **Not separate approval gates** — after WP-C passes its checks, continue automatically as one coordinated Phase 7 completion effort |
| Phase 8+ | **Blocked** until Phase 7 exit criteria (§15) are met and `docs/PHASE7_FINAL_COMPLETION_REPORT.md` exists |

**Still stop and request approval when:**

- A critical issue blocks progress.  
- A change would modify Phase 1–6 behavior, backend payment invariants, database business rules, or expand approved product scope.  
- Unrelated refactoring or out-of-plan dependency upgrades are proposed.

**Do not** treat this workflow change as permission to weaken tests, invent APIs outside the approved plan, use Stripe Live Mode, or claim production readiness.

**Phase 7 complete only when:** WP-A…G functionality is implemented; customer checkout + Test Mode pay work E2E; admin dashboard/management work; shipping transitions + history work; refund reconciliation works with provider verification + append-only audit; critical Playwright flows pass; backend + frontend regression suites pass; Compose local stack works; docs + `TASK_TRACKER.md` updated; `docs/PHASE7_FINAL_COMPLETION_REPORT.md` produced.

---

## 11. Testing strategy

| Layer | Coverage |
|-------|----------|
| Backend unit | Transition rules; KPI formulas; user safety predicates; refund verify matchers |
| Backend IT (Postgres Testcontainers) | Shipping transitions + history; refund confirm success / PI mismatch / charge-path / amount-currency / non-succeeded / uncertain; concurrent confirm; audit append-only; dashboard **payment-period** KPIs; **concurrent mutual ADMIN disable** (≥1 ACTIVE ADMIN remains); self-disable |
| FakePaymentProvider | Scripted retrieveRefund (+ optional fake Charge association) |
| FE unit | Vitest + RTL guards, forms, refund/shipping UI states |
| Playwright | Customer purchase path; Admin dashboard/manage/fulfill; CUSTOMER denied `/admin`; CI only if stable (P7-D15) |
| Regression | Do **not** weaken Phase 1–6 Surefire/Failsafe |
| Responsive | Manual + selective Playwright viewports |

### Required negative / security cases

- Invalid / mismatched / unowned Stripe Refund ID → not `REFUNDED`  
- Charge belongs to different PaymentIntent → reject  
- Amount/currency/status/`livemode` mismatch → reject  
- Concurrent reconciliation → one winner, consistent final state  
- Audit rows not updatable via API  
- Invalid shipping transitions (e.g. PAID→SHIPPED skip, admin→PAID)  
- ADMIN self-disable; sole ADMIN cannot leave zero ACTIVE ADMINs  
- **Concurrent A↔B ADMIN disable/suspend → ≥1 ACTIVE ADMIN remains**  
- Revenue KPI uses `succeeded_at`/`refunded_at`, not `orders.created_at` / `refunds.created_at`  
- Elements success without webhook does not show authoritative PAID

---

## 12. Local development & testing instructions (planned)

```bash
# Infra
docker compose -f infra/docker/docker-compose.yml up -d postgres redis kafka mailhog

# Backend
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# Frontend
cd frontend && cp .env.example .env.local
# VITE_API_BASE=/api/v1
# VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...
npm run dev

# Stripe CLI (webhook authority)
stripe listen --forward-to localhost:8080/api/v1/payments/webhook/stripe

# Playwright (after app up)
cd frontend && npx playwright test

# Full stack
docker compose -f infra/docker/docker-compose.yml --profile app up -d --build
```

CI: add Playwright job only when flake rate acceptable; otherwise nightly/manual until stable (P7-D9).

---

## 13. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| FE treats Elements as paid | Copy + poll; webhook authority |
| Admin-trusted Refund ID (pre-P7 gap) | P7-D10 mandatory provider verify |
| Provider timeout marked success | Uncertain path; audit; no status change |
| Admin forge PAID | Transition tests + forbid in API |
| Last admin locked out / race to zero ADMINs | Advisory/`FOR UPDATE` serialization + concurrent Postgres IT |
| Revenue from checkout/booking timestamps | V13 `succeeded_at`/`refunded_at`; payment-period KPIs only |
| Mixed-currency revenue nonsense | Per-currency aggregates only |
| Charge-only Stripe refunds mis-attributed | Mandatory Charge→PI retrieve before accept |
| Playwright flake in CI | Local-first; CI when stable (P7-D15) |
| H2 vs PG migrations | Portable V11–V13; keep V10 PG-only |
| Scope creep (returns, shipments, roles) | Locked P7-D1…D15 |

---

## 14. Dependencies

- Phase 6 payment/outbox/refund services remain stable.  
- Stripe Test keys + CLI.  
- Docker for Failsafe / Compose / Playwright.  
- Node 22+ for frontend.  
- New provider capability: `retrieveRefund` (+ Charge retrieve when PI absent on Refund).  
- V13 completion timestamps wired on payment success / refund success paths (without changing money-path invariants).  

---

## 15. Phase 7 acceptance criteria (exit)

1. Customer: register, browse, cart, checkout, pay (Test Mode), orders, cancel unpaid.  
2. UI never treats Elements alone as `PAID`; webhook-driven status reflected.  
3. Admin: catalog, inventory (reason), promotions, dashboard KPIs (§7 payment-period revenue), user status (safe under concurrency), fulfillment with history/tracking.  
4. Refund recon: provider-verified confirm (PI/Charge path, amount, currency, `succeeded`, Test Mode); reopen reuses idempotency key; audits append-only; no new refund row.  
5. CUSTOMER cannot access admin; ADMIN cannot forge `PAID`, change roles, or shop via cart/pay; no post-`PAID` cancel/returns.  
6. Compose profile `app` runs backend+frontend.  
7. Backend regression green; FE unit + Playwright critical paths green locally.  
8. Docs updated; **no** production-readiness claim.  

---

## 16. Decisions P7-D11…D15 (lock on plan approval)

P7-D1…D10 remain locked as previously approved. The following defaults become **locked upon explicit approval of this plan** unless overridden in the approval message:

| ID | Topic | Value locked on approval |
|----|--------|--------------------------|
| **P7-D11** | Token storage | Access token in **memory**; refresh token in **sessionStorage** |
| **P7-D12** | Low-stock threshold | Default **5** (`app.inventory.low-stock-threshold`) |
| **P7-D13** | Order status history | Record **admin and system/payment** transitions |
| **P7-D14** | Product counts | `totalProducts` = all; expose `activeProducts` separately |
| **P7-D15** | Playwright CI | Enable in CI **only if** stable; otherwise local/`npm run test:e2e` |

No other blocking architecture questions remain for planning.

---

## 17. Final consistency checklist

| Check | Status |
|-------|--------|
| Flyway: portable **V11–V13** after shared **V9**; PG-only **V10** untouched | Planned |
| Phase 1–6 money-path invariants preserved (webhook authority, no forge-PAID, refund idempotency) | Required |
| No admin role-changing API | Locked P7-D5 |
| No post-PAID cancellation / returns / restock | Locked P7-D7 |
| No admin forge-PAID | Locked (transitions + tests) |
| No new refund row / new Stripe idempotency key on retry | Locked P6 + P7-D10 |
| Single major Phase 7 with WP-A…G only | Confirmed |
| Acceptance criteria ↔ KPI §7 ↔ security §8 ↔ tests §11 | Aligned in this revision |

---

## 18. Exclusions reminder / workflow note

Historical planning revisions were documentation-only until plan approval.  
**Current policy (§10.1):** WP-C is in progress; after WP-C verification, implement WP-D…G continuously without per-WP approval stops.  
**Do not start Phase 8** until §15 exit criteria and `docs/PHASE7_FINAL_COMPLETION_REPORT.md` are satisfied.  
Unrelated workflow doc updates must not change Phase 1–6 behavior or expand product scope.
