# Security Guide

## Phase 2 authentication model

| Item | Design |
|------|--------|
| Access token | JWT (HS256), short-lived, claims: `sub`, `email`, `roles`, `iss`, `exp`, `jti` |
| Access revocation | Redis (or in-memory) denylist keyed by `jti` until access-token expiry |
| Authorization | Per-request DB load of user status + roles; JWT role claims are not authoritative |
| Refresh token | Opaque random value, **SHA-256 hashed** at rest |
| Refresh rotation | `@Transactional` + `SELECT … FOR UPDATE` (`PESSIMISTIC_WRITE`); family reuse detection |
| Password | BCrypt strength 12 |
| Session | Stateless |
| Roles | `ADMIN`, `CUSTOMER` (authorities `ROLE_ADMIN`, `ROLE_CUSTOMER`) |
| Account status | `ACTIVE`, `DISABLED`, `SUSPENDED` — only `ACTIVE` may authenticate |
| Rate limiting | Redis-backed (in-memory fallback) on login / register / refresh; HTTP 429 |

## Public endpoints

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/platform/**`
- `GET /api/v1/categories`, `GET /api/v1/categories/{id|slug}`
- `GET /api/v1/products`, `GET /api/v1/products/{id|slug}` (ACTIVE products only)
- `GET /actuator/health` (+ liveness/readiness under health)
- Optional (profile flags): `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/prometheus`, `/actuator/info`

## Authenticated endpoints

- `POST /api/v1/auth/logout` — revokes current access `jti` **and** refresh token
- `GET /api/v1/auth/me`
- `GET /api/v1/customer/**` — `CUSTOMER` or `ADMIN` (DB roles)
- `/api/v1/cart/**`, `/api/v1/orders/**`, `/api/v1/promotions/**` — `CUSTOMER` only; **ADMIN denied** (even if also CUSTOMER); ownership enforced on cart/orders
- `/api/v1/admin/**` — `ADMIN` only (DB roles), including inventory, promotions, orders

## Privilege escalation controls

- Registration **always** assigns `CUSTOMER` only.
- Clients cannot request or self-assign `ADMIN`.
- ADMIN assignment is out-of-band (seed/admin tooling in later phases).
- Demotion / disable / suspend takes effect on the **next** authenticated request (DB revalidation).

## Access token revocation (logout)

1. Logout parses the Bearer access token, extracts `jti`, and stores it in the denylist with TTL = remaining JWT lifetime.
2. Logout also revokes the presented refresh token (and family rules as applicable).
3. `JwtAuthenticationFilter` rejects denylisted JTIs before building the security context.
4. **Redis unavailable on denylist check:** fail-closed (`503` / denylist unavailable) so revoked tokens cannot slip through.

## Refresh token rotation and reuse

1. Each refresh issues a new raw token and revokes the previous one under a row lock.
2. Tokens share a `family_id`.
3. Presenting a revoked token triggers family revocation (reuse detection).
4. Concurrent refresh of the same token: exactly one rotation succeeds.

## Rate limiting

Configurable via environment variables (defaults in `application.yml`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `RATE_LIMIT_ENABLED` | `true` | Master switch |
| `RATE_LIMIT_LOGIN_PER_MINUTE` | `10` | Per IP + normalized email |
| `RATE_LIMIT_REGISTER_PER_MINUTE` | `5` | Per IP |
| `RATE_LIMIT_REFRESH_PER_MINUTE` | `30` | Per IP |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | Fixed-window length |
| `RATE_LIMIT_CHECKOUT_PER_ACCOUNT_PER_MINUTE` | `30` | Checkout account bucket (Phase 8) |
| `RATE_LIMIT_CHECKOUT_PER_IP_PER_MINUTE` | `60` | Checkout IP bucket |
| `RATE_LIMIT_PAY_PER_ACCOUNT_PER_MINUTE` | `30` | Pay account bucket |
| `RATE_LIMIT_PAY_PER_IP_PER_MINUTE` | `60` | Pay IP bucket |
| `RATE_LIMIT_ADMIN_REFUND_CONFIRM_PER_ACCOUNT_PER_MINUTE` | `20` | Admin confirm-external |
| `RATE_LIMIT_ADMIN_REFUND_CONFIRM_PER_IP_PER_MINUTE` | `40` | |
| `RATE_LIMIT_ADMIN_REFUND_REOPEN_PER_ACCOUNT_PER_MINUTE` | `10` | Admin reopen-and-submit |
| `RATE_LIMIT_ADMIN_REFUND_REOPEN_PER_IP_PER_MINUTE` | `20` | |

Authenticated write limits use **account + IP** keys evaluated **atomically** in Redis (Lua). On reject, sibling under-budget counters are **not** incremented. Stripe webhooks are **excluded** from this business API limiter.

Exceeded limits return **HTTP 429** with code `RATE_LIMIT_EXCEEDED` (no account enumeration details). No permanent lockouts.

**Redis unavailable on rate-limit increment:** fail-open (availability) — brief burst risk until Redis recovers. Denylist checks remain fail-closed.

### Trusted proxies (Phase 3)

`ClientIpResolver` uses `X-Forwarded-For` / `X-Real-IP` **only** when `request.getRemoteAddr()` matches `app.security.trusted-proxies` / `TRUSTED_PROXIES` (exact IP or CIDR). Empty list (default) ⇒ forwarding headers are ignored; rate-limit keys cannot be spoofed by clients.

## Catalog Redis cache (Phase 3)

Category tree and product detail use cache-aside with write-path invalidation. Redis outage on catalog cache: **fail-open** (DB fallback). This does not change auth denylist or rate-limiter semantics.

## Inventory & cart (Phase 4)

- Stock mutations: ADMIN delta adjust with mandatory reason; audit row (`actor`, `delta`, timestamps).
- Optimistic `@Version` + non-negative CHECK constraints; concurrent conflicts → HTTP 409.
- Internal reserve/release/commit only (no public HTTP); reserve/release/commit **join caller TX** (Phase 5).
- Cart does not reserve inventory; validates ACTIVE product + available qty.
- IDOR: cart operations scoped to authenticated `userId` only.

## Orders & promotions (Phase 5)

- Checkout requires `Idempotency-Key`; UNIQUE `(user_id, key)`; payload mismatch → 409.
- Order line prices from **current catalog**, not cart snapshots.
- Customer sees/cancels only own unpaid (`PENDING_PAYMENT`) orders.
- Admin status: only `PENDING_PAYMENT` → `CANCELLED` | `PAYMENT_FAILED` (no mark-paid / no `PAID`).
- Cancel / `PAYMENT_FAILED`: release reserved stock once + restore promo `usedCount` once.
- Promo `usedCount` increments only in successful checkout TX.

## Payments (Phase 6)

- Stripe **Test Mode** only. Keys come from `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, and `STRIPE_WEBHOOK_SECRET`. Tests use `app.stripe.mock=true` and never call Stripe.
- Card PAN/CVV are never accepted or stored. `client_secret` is not persisted and is not written to logs; duplicate pay loads it from the provider.
- `POST /payments/webhook/stripe` is public and rejects invalid signatures. Order totals and currency are taken from the order row.
- Only a verified success webhook may move `PENDING_PAYMENT` → `PAID` and commit inventory once. Admin APIs still cannot mark an order paid.
- `CANCELLED` and `PAYMENT_FAILED` stay terminal. A later success books one refund (`LATE_SUCCESS`) via the transactional outbox. `REFUND_SUBMITTED` is not `REFUNDED`. Uncertain provider results stay `RECONCILIATION_REQUIRED` for admin reconciliation (Phase 7 UI + APIs).
- Cancel requests the provider cancellation only after the order transaction commits, and the local payment stays `CANCEL_REQUESTED` until the provider confirms.
- There is no re-pay on `PAYMENT_FAILED`.
- At most one open payment attempt per order is enforced in PostgreSQL via `payments.active_lock_order_id` UNIQUE and partial unique index `uq_payments_one_open_attempt_per_order` (PG-only Flyway location). Concurrent pay losers reuse the winner’s PaymentIntent.
- Recovery for `RECONCILIATION_REQUIRED` (P7-D10): admin `confirm-external` treats `providerRefundRef` as a **lookup key only**; backend must `retrieveRefund` outside the DB TX and verify id, `succeeded` status, amount, currency, PaymentIntent (or Charge→PI), and Test Mode before `REFUNDED`. Audits are append-only. `reopenAndSubmit` reuses the same `idempotency_key` only when `provider_refund_ref` is null. Never invent a new idempotency key; never unrestricted SQL status flips; never mark terminal unpaid orders `PAID`.


## Actuator & API documentation exposure

| Flag | Prod/Docker default | Local/standalone |
|------|---------------------|------------------|
| `API_DOCS_PUBLIC` / `app.security.api-docs-public` | `false` | `true` |
| `ACTUATOR_PROMETHEUS_PUBLIC` / `app.security.actuator-prometheus-public` | `false` | `true` |
| `ACTUATOR_PROMETHEUS_USERNAME` / `PASSWORD` | set in Compose for scrape | optional |

Docker scrape: keep prometheus **non-public**; use HTTP Basic (`ACTUATOR_PROMETHEUS_USERNAME` / `PASSWORD`) over the Compose network. See `docs/PHASE8_OBSERVABILITY_RUNBOOK.md`.

**Headers (Phase 8):** CSP is **report-only**; HSTS only on HTTPS. Do not enforce CSP until SPA/proxy/Stripe checklist PASS (`docs/PHASE8_SECURITY_REVIEW.md`).

Production also disables springdoc when configured in `application-prod.yml`. Health remains publicly reachable for orchestration probes.

## Secrets

Configure via environment variables (never commit real values):

```bash
JWT_SECRET=<at-least-32-bytes-cryptographically-random>
JWT_ISSUER=ecommerce-platform
JWT_ACCESS_TTL_MINUTES=15
JWT_REFRESH_TTL_DAYS=14
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
STRIPE_SECRET_KEY=sk_test_replace_me
STRIPE_PUBLISHABLE_KEY=pk_test_replace_me
STRIPE_WEBHOOK_SECRET=whsec_replace_me
ENFORCE_SECURE_JWT_SECRET=true   # enforced automatically for prod/docker
```

- **local / standalone:** may use documented **dev-only** defaults when `JWT_SECRET` is unset.
- **prod / docker:** `JwtSecretsValidator` fails fast if secret is missing, too short, or matches known vulnerable defaults. Compose does **not** default `JWT_SECRET`.

## Related reports

- Audit (read-only): `docs/PHASE2_SECURITY_AUDIT.md`
- Remediation: `docs/PHASE2_SECURITY_REMEDIATION_REPORT.md`
