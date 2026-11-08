# Phase 8 Implementation Plan — Hardening & Observability

**Date:** 2026-11-05 (revised 2026-11-06; U1–U6 resolved; atomic multi-bucket RL rule added)  
**Status:** **PLANNED — AWAITING APPROVAL** (planning only; **no implementation**)  
**Prior phase:** Phase 7 accepted for local portfolio delivery with limitations (`docs/PHASE7_FINAL_ACCEPTANCE_REPORT.md`)  
**Exit criterion (DEVELOPMENT_PLAN §8):** Dashboards + SLOs documented; rate limiting expanded; observability (OTel/Prometheus/Grafana) usable locally  
**Constraint:** Local-first; **no** cloud deploy, **no** paid AWS, **no** Stripe Live Mode, **no** Phase 9

---

## 0. Scope statement

Phase 8 is **one major phase**. Internal work packages (WP-A…F) organize delivery. Do **not** create Phase 8.1 as a top-level roadmap phase.

### In scope (from DEVELOPMENT_PLAN + TASK_TRACKER)

| Area | Intent |
|------|--------|
| Rate limiting | Expand beyond auth login/register/refresh to checkout, pay, and admin refund write APIs (Redis-backed; explicit keys/budgets) |
| Security review | Headers (CSP report-only first), HTTPS-aware HSTS, secrets hygiene, admin lockdown; Actuator stays protected by default |
| Prometheus | Scrape `/actuator/prometheus` securely on the Docker internal network **without** making the endpoint public |
| Grafana | Local dashboards under Compose `observability` profile (HTTP, JVM, Redis/Kafka, HikariCP, rate-limit rejections) |
| OpenTelemetry | Optional Micrometer Tracing → OTel export; env-gated |
| Correlation IDs | Validated incoming IDs; prefer W3C `traceparent`; generate `X-Correlation-Id` otherwise; MDC cleared per request |
| Tests | Explicit Phase 8 control tests + Phase 1–7 regression; address known Vitest flake |
| Docs | SLOs, observability runbook, security review, SECURITY/API updates as needed |

### Out of scope

| Item | Notes |
|------|-------|
| Kubernetes / Helm | Phase 9 |
| AWS / EKS / paid infra | Phase 10 |
| Changing Phase 1–7 money-path invariants | Forbidden without explicit approval |
| Stripe Live Mode / production certification | Forbidden |
| New commerce features (returns, shipments domain, roles API) | Rejected earlier; stay out |
| Replacing Redis/Kafka/Postgres stack | Not needed |
| Heavy APM SaaS or multi-cluster mesh | Over-engineering |
| Enabling `ACTUATOR_PROMETHEUS_PUBLIC=true` solely for scraping | Forbidden (P8-D10) |
| Enforcing CSP before SPA/proxy/Stripe compatibility check | Forbidden (start report-only) |
| Ordinary business API rate limiter on Stripe webhooks | Forbidden (U2 — excluded; preserve signature + idempotent retries) |
| High-cardinality metric labels (order ID, PaymentIntent ID, email, JWT) | Forbidden |
| DB migration unless later proven necessary | Stop and request approval (P8-D7) |

---

## 1. Current-state inspection (code vs docs)

### 1.1 What already exists

| Capability | Reality |
|------------|---------|
| Auth rate limit | `AuthRateLimiter` + Redis/InMemory; wired on login/register/refresh (`AuthService`) |
| Rate-limit config | `app.security.rate-limit.*` — `enabled`, `login/register/refresh-per-minute`, `window-seconds` |
| Trusted proxies / ClientIp | `ClientIpResolver`; forwarding headers only when remote addr matches trusted proxies |
| Redis rate-limit outage | **Fail-open** (documented in `docs/SECURITY.md`) |
| Actuator | Health public; `prometheus` + `metrics` exposed in management config |
| Micrometer Prometheus | `micrometer-registry-prometheus` on classpath |
| Prometheus public flag | `ACTUATOR_PROMETHEUS_PUBLIC` / docker & prod defaults **false**; `application-local` / standalone / many tests set **true** for convenience |
| Actuator denial test | `ActuatorExposureTest` expects 401 when flag false |
| Compose app stack | Postgres, Redis, Kafka, Mailhog, backend, frontend (`profile: app`) |
| Phase 7 security | JWT docker secret validation; admin RBAC; refund provider verify (P7-D10) |

### 1.2 Gaps vs DEVELOPMENT_PLAN Phase 8

| Planned | Gap |
|---------|-----|
| Expand Redis rate limiting | Only auth endpoints rate-limited today |
| Admin / checkout / pay sensitive POSTs | No dedicated limits |
| Explicit key model (account vs IP) | Auth uses IP (+ email for login); no account-id key for authenticated writes |
| Grafana + Prometheus services | **Not** in Compose; no `observability` profile |
| Secure scrape without public Actuator | Not designed yet (today scrape needs flag true or JWT — neither is ideal for local Compose scrape) |
| OpenTelemetry / Micrometer Tracing | Not on classpath |
| Correlation IDs / MDC | No filter; no validation/cleanup convention |
| Security headers pass | No explicit CSP report-only / conditional HSTS beyond Spring defaults |
| Dashboards + SLOs | Missing |

### 1.3 Phase 7 acceptance limitations relevant to Phase 8

| Limitation | Phase 8 treatment |
|------------|-------------------|
| Credentialed Stripe / admin E2E NOT VERIFIED | Document local checklist; optional WP-E smoke if secrets available — do not block Phase 8 exit on Live Stripe |
| Vitest CheckoutPage flake | Fix under WP-E (test stability only) |
| Playwright CI deferred (P7-D15) | Keep deferred unless flake budget met; local `npm run test:e2e` remains |
| `.env.example` JWT cannot start docker alone | Document operator step in Phase 8 runbook (correct security behavior) |

---

## 2. Decisions (proposed — lock on approval)

| ID | Topic | Proposed choice |
|----|-------|-----------------|
| **P8-D1** | Rate-limit expansion | Extend Redis limiter to checkout, pay, admin refund confirm/reopen with **explicit keys/budgets** (§4 WP-A); **fail-open on Redis outage** (same as auth) |
| **P8-D2** | Money-path under limit | Rate limit returns **429 before any checkout/pay/refund side effects**; preserve Idempotency-Key + PaymentIntent reuse; legitimate retries within budget must succeed |
| **P8-D3** | Observability stack | Compose profile `observability`: Prometheus + Grafana; scrape backend `/actuator/prometheus` on **Docker internal network** with scrape credentials (not by opening Actuator publicly); Grafana host port **≠ 3000** (e.g. `3001`); Prometheus `9090` |
| **P8-D4** | OTel | Add Micrometer Tracing + OTel bridge; export only when `OTEL_EXPORTER_OTLP_ENDPOINT` (or equivalent) set; default local = metrics-first |
| **P8-D5** | Correlation ID | See **U3** + **U5** (resolved): validate; ignore invalid; prefer active W3C/Micrometer trace context when tracing on; stable `X-Correlation-Id` when tracing off; MDC cleared per request |
| **P8-D6** | Security headers | Baseline headers; **CSP report-only** through Phase 8 unless promotion criterion met (**U4**); **HSTS only when HTTPS** |
| **P8-D7** | Migrations | **None expected**; if schema needed during implementation → **stop and request approval** |
| **P8-D8** | Frontend | Minimal: map HTTP 429 on checkout/pay/admin refund; optional correlation display — not required |
| **P8-D9** | Continuity | After approval, implement WP-A→F without per-WP approval gates; stop for money-path/scope/migration issues |
| **P8-D10** | Actuator / scrape | Keep Actuator **protected by default**. **Do not** set `ACTUATOR_PROMETHEUS_PUBLIC=true` for scrape convenience. Scrape auth = **U1** (Docker-network Basic Auth via env) |
| **P8-D11** | Webhook rate limit | **U2 resolved:** do **not** apply ordinary business API rate limiter to Stripe webhooks |
| **P8-D12** | Metric cardinality | Never use order IDs, PaymentIntent IDs, emails, JWTs, or other unbounded identifiers as metric **labels**. Business meters are **observational only** — never payment/order status authority |
| **P8-D13** | Logging redaction | Never log JWTs, refresh tokens, Stripe client secrets, PAN, or raw secrets |
| **P8-D14** | Rate-limit budgets | **U6 resolved:** §4 WP-A.2 local defaults are the locked configurable baselines (not production traffic assumptions) |
| **P8-D15** | Atomic multi-bucket RL | When account + IP both apply: evaluate/update **atomically** in Redis (Lua or equivalent). Reject without incrementing sibling buckets still under budget. See WP-A.1 / WP-A.4 |

Approving this plan locks **P8-D1…D15** and **resolved U1–U6** (§9) unless overridden in the approval message.

---

## 3. Architecture (target)

```text
Client ──► Frontend (Nginx :3000)
              │ /api proxy
              ▼
           Backend (:8080)
              ├─ RateLimit (Redis) — before side effects
              ├─ Correlation / Tracing filter → MDC (cleared in finally)
              ├─ Security headers (CSP report-only; HSTS if HTTPS)
              ├─ Business modules (unchanged money authority)
              ├─ /actuator/health (public probes)
              └─ /actuator/prometheus (authenticated; not public by default)

Prometheus (profile observability)
   ── Docker network ──► backend:8080/actuator/prometheus
   (scrape credentials; ACTUATOR_PROMETHEUS_PUBLIC remains false)

Grafana ──► Prometheus + provisioned dashboards
Optional OTLP collector when OTEL_EXPORTER_OTLP_ENDPOINT set
```

**Port guidance (local):** frontend host `3000`; Grafana host e.g. `3001`; Prometheus `9090`.

---

## 4. Work packages

### WP-A — Rate limiting expansion

| | |
|--|--|
| **Objective** | Protect sensitive write APIs with Redis-backed limits without harming money-path correctness, idempotency, admin authz, or Stripe webhooks |
| **Scope** | Generalize or extend rate-limit components beyond auth; apply to listed routes with explicit keys/budgets below; configurable local defaults |
| **Exclusions** | CAPTCHA; fail-closed Redis (remain fail-open); **any** ordinary business rate limiter on Stripe webhooks (U2); using order/PI IDs as metric labels |
| **Backend** | Shared limiter API (extend `AuthRateLimiter` or sibling `ApiRateLimiter`); wire **before** checkout/pay/refund mutations; **atomic multi-bucket check** (WP-A.4); preserve ADMIN `@PreAuthorize` / `hasRole("ADMIN")` — rate limit is **additional**, not a substitute for authz |
| **Frontend** | Clear UX for HTTP 429 on checkout, pay, admin refund confirm/reopen |
| **Database** | **None** — no Flyway migration (P8-D7) |
| **API** | No contract field changes; **429** + existing error body shape; optional `Retry-After` if already consistent with auth |
| **Security** | Trusted-proxy / `ClientIpResolver` unchanged; prefer **authenticated account id** for authenticated endpoints; IP as secondary/abuse layer where appropriate; keys must not be spoofable |
| **Tests** | See §5.1 (rate-limit suite), including atomic dual-bucket cases T9–T13 |
| **Acceptance** | Documented keys/budgets; atomic dual-bucket behavior proven; tests green; normal checkout/pay/idempotent retry succeed; webhook not incorrectly blocked; Redis fail-open preserved |
| **Depends on** | None |

#### WP-A.1 Rate-limit keys (explicit)

| Route | Authz (unchanged) | Primary key | Secondary / abuse key | Redis key shape (illustrative) |
|-------|-------------------|-------------|------------------------|--------------------------------|
| `POST /api/v1/orders/checkout` | CUSTOMER | `accountId` (JWT subject / user id) | Client IP (trusted-proxy rules) | `rl:checkout:acct:{id}`, `rl:checkout:ip:{ip}` |
| `POST /api/v1/orders/{id}/pay` | CUSTOMER (owner) | `accountId` | Client IP | `rl:pay:acct:{id}`, `rl:pay:ip:{ip}` |
| `POST /api/v1/admin/refunds/{id}/confirm-external` | ADMIN | `accountId` (admin user) | Client IP | `rl:admin-refund-confirm:acct:{id}`, `…:ip:{ip}` |
| `POST /api/v1/admin/refunds/{id}/reopen-and-submit` | ADMIN | `accountId` | Client IP | `rl:admin-refund-reopen:acct:{id}`, `…:ip:{ip}` |
| Existing auth login | public | IP + email (as today) | — | unchanged |
| Existing auth register / refresh | public | IP (as today) | — | unchanged |
| `POST /api/v1/payments/webhook/stripe` | Stripe signature verify (unchanged) | **N/A — excluded (U2)** | **N/A** | Do **not** wire ordinary business API rate limiter |

**Rules:**

1. **Authenticated endpoints:** primary budget is **account identity**, not order id / PaymentIntent id / Idempotency-Key.  
2. **IP keys:** defense-in-depth against distributed abuse of a single account or credential stuffing adjacency; still subject to trusted-proxy rules.  
3. **Both buckets (account + IP):** when both apply, evaluation and counter updates are **atomic** per WP-A.4 — allow only if **all** applicable buckets are within budget; on reject, **do not** burn quota on buckets that were still under budget.  
4. **Path `{id}`** is for routing only — **never** part of the rate-limit key or Prometheus label.  
5. **ADMIN authorization preserved:** unauthenticated / non-ADMIN still get 401/403 before or independent of 429; limits apply to **authorized** admin refund actions as well.  
6. **Stripe webhook (U2):** excluded from WP-A business API rate limiting. Preserve signature verification, idempotent event processing, and safe Stripe retries. Any infrastructure-level DoS protection (reverse proxy, Compose network exposure) is **documented separately** in SECURITY / observability runbook — **not** implemented as the app `ApiRateLimiter` on the webhook path.

#### WP-A.2 Budgets — locked local-development defaults (U6)

**Window:** reuse `app.security.rate-limit.window-seconds` (default **60**).  
**Algorithm:** **fixed-window** counters (same window semantics as existing auth Redis limiter: first hit sets TTL = window; reject when projected count would exceed budget). No separate token-bucket “burst” beyond the per-window budget — the budget **is** the allowed count per window.  
**Multi-bucket update:** for account+IP routes, counters are read/checked/incremented via **atomic Redis operation** (WP-A.4) — **not** two independent `INCR` calls that can leave asymmetric state on partial reject.  
**Keying:** account primary + IP secondary as in WP-A.1 (**unchanged**); both must pass when both apply.  
**Budgets:** numeric defaults in the table below are **locked** — do not change as part of this atomic-evaluation rule.  
**Scope of numbers:** **local development / demo only** — not production traffic assumptions or SLAs.  
**Overrides:** environment variables below; raise for demos if needed; document final property names in SECURITY.md at implementation.

| Bucket property | Env override | Local default (per window) | Rationale |
|-----------------|--------------|----------------------------|-----------|
| `checkout-per-account-per-minute` | `RATE_LIMIT_CHECKOUT_PER_ACCOUNT_PER_MINUTE` | **30** | Demo + legitimate idempotent retries |
| `checkout-per-ip-per-minute` | `RATE_LIMIT_CHECKOUT_PER_IP_PER_MINUTE` | **60** | Shared NAT friendliness locally |
| `pay-per-account-per-minute` | `RATE_LIMIT_PAY_PER_ACCOUNT_PER_MINUTE` | **30** | Retry UX; PI reuse must not be starved |
| `pay-per-ip-per-minute` | `RATE_LIMIT_PAY_PER_IP_PER_MINUTE` | **60** | Same |
| `admin-refund-confirm-per-account-per-minute` | `RATE_LIMIT_ADMIN_REFUND_CONFIRM_PER_ACCOUNT_PER_MINUTE` | **20** | Admin tooling; still limited |
| `admin-refund-confirm-per-ip-per-minute` | `RATE_LIMIT_ADMIN_REFUND_CONFIRM_PER_IP_PER_MINUTE` | **40** | |
| `admin-refund-reopen-per-account-per-minute` | `RATE_LIMIT_ADMIN_REFUND_REOPEN_PER_ACCOUNT_PER_MINUTE` | **10** | More sensitive; lower |
| `admin-refund-reopen-per-ip-per-minute` | `RATE_LIMIT_ADMIN_REFUND_REOPEN_PER_IP_PER_MINUTE` | **20** | |
| Existing login/register/refresh | existing env keys | **10 / 5 / 30** | Unchanged |
| Stripe webhook | — | **Excluded (U2)** | No app-level business rate limiter |

Tuning after E2E is allowed **only** via documented env overrides with test evidence — defaults above remain the plan baseline.

#### WP-A.3 Money-path & retry semantics

| Concern | Rule |
|---------|------|
| Side effects | Increment/check limiter **before** creating orders, reserving stock, creating/confirming PaymentIntents, or mutating refund rows |
| Checkout idempotency | Same `Idempotency-Key` + same account within budget → success path unchanged; 429 must not leave partial checkout state |
| PaymentIntent reuse | Pay endpoint reuse behavior from Phase 6/7 preserved; rate limit must not force “new PI” side effects |
| Legitimate retries | Defaults sized so normal FE retry / double-submit within a short window is not incorrectly blocked; document how to raise knobs for demos |
| Redis outage | **Fail-open** (allow request; log warn) — same as current auth limiter; applies to the atomic multi-bucket script/operation as a whole |
| Stripe webhook | **Excluded** from ordinary business API rate limiter (U2); never return app rate-limit 429 that interferes with signed Stripe retries |

#### WP-A.4 Atomic account + IP rate-limit evaluation (required — P8-D15)

When **both** account and IP buckets apply to a request (checkout, pay, admin refund confirm/reopen), Redis must evaluate and update them **atomically**.

**Preferred implementation:** a **Redis Lua script** (or an equivalent single atomic Redis operation / transaction that provides the same guarantees). Sequential non-atomic `GET`/`INCR` pairs across keys are **not** acceptable for the dual-bucket path.

**Required behavior:**

1. **Read/check** all applicable bucket counters for the current fixed window.  
2. If **ANY** applicable bucket has already reached/exceeded its configured budget (or would exceed it if this request were counted), **reject** with HTTP **429**.  
3. On reject: **do NOT increment** any sibling bucket that was still within budget (no “tax” on the other dimension).  
4. If **all** applicable buckets allow the request, **increment all** applicable counters **atomically** in the same operation.  
5. Preserve existing **fixed-window 60-second** semantics (`window-seconds` default 60; TTL set on first increment in the window, consistent with current auth limiter).  
6. Preserve Redis **fail-open**: if the atomic script/operation fails due to Redis unavailability/errors, **allow** the request and log a warning (same availability posture as auth RL).  
7. **No database migration** — Redis keys only; no Flyway / schema change.

**Non-goals for this rule:** changing locked local budgets (WP-A.2), changing account+IP keying (WP-A.1), fail-closed Redis, or applying this limiter to Stripe webhooks.

**In-memory / test doubles:** unit tests may simulate the same allow/reject-without-sibling-increment semantics; Redis ITs must exercise the real atomic path.

---

### WP-B — Security review & hardening

| | |
|--|--|
| **Objective** | Close obvious hardening gaps; produce an auditable security checklist against Phase 1–7 |
| **Scope** | Security headers per P8-D6; Actuator exposure defaults (P8-D10); admin lockdown confirmation; secrets/JWT docker guidance; lightweight dependency review; residual risks doc |
| **Exclusions** | Full pentest; WAF; JWT algorithm change; cookie session rewrite; enforcing CSP in Phase 8 without compatibility evidence |
| **Backend** | SecurityFilterChain headers: `X-Content-Type-Options nosniff`, `X-Frame-Options` DENY or SAMEORIGIN, Referrer-Policy; **CSP Report-Only** header initially; **HSTS only when HTTPS** (e.g. only if `request.isSecure()` / forwarded proto https **and** trusted proxy — never on plain local HTTP); confirm prometheus stays non-public by default |
| **Frontend** | Compatibility checklist for CSP report-only vs React SPA, Vite/nginx `/api` proxy, Stripe.js / Payment Element script/connect sources; **no enforce until checklist PASS**; keep `assertStripeTestPublishableKey` |
| **Database** | None |
| **API** | Response headers only (additive) |
| **Security** | Primary WP; Actuator protected by default |
| **Tests** | Headers present; CSP is report-only (not enforce) until approved flip; HSTS absent on HTTP local; Actuator prometheus **denied** when public flag false; SPA smoke still loads |
| **Acceptance** | `docs/PHASE8_SECURITY_REVIEW.md` (created at implementation); checklist PASS/FAIL; **no** scrape workaround via public Actuator |
| **Depends on** | None (parallel with WP-A) |

#### WP-B.1 CSP / HSTS / Actuator rules

1. **CSP (U4):** ship **report-only** for Phase 8. Enforcement stays **disabled** until promotion criterion below is met and explicitly recorded.  
2. **CSP promotion criterion (required before enforce):** all of the following documented PASS in `docs/PHASE8_SECURITY_REVIEW.md`: (a) React SPA loads under report-only with no blocking-class violations for app scripts/styles; (b) nginx/`/api` proxy requests succeed without CSP breakage; (c) Stripe.js / Payment Element (Test Mode) loads and completes a local payment Element flow without CSP violations that would block under an equivalent enforcing policy; (d) explicit human approval to flip report-only → enforce (may be a follow-on change after Phase 8 exit if criterion not met in-phase). **Default Phase 8 exit:** remain report-only.  
3. **HSTS:** emit `Strict-Transport-Security` **only** when the effective request scheme is HTTPS. Local Compose/HTTP must remain usable without HSTS breakage.  
4. **Actuator:** default `actuator-prometheus-public=false` for docker/prod-like profiles. Health probes stay as today. **Forbidden:** flipping `ACTUATOR_PROMETHEUS_PUBLIC=true` just to green Prometheus (U1 / P8-D10).

---

### WP-C — Prometheus metrics & Grafana (local)

| | |
|--|--|
| **Objective** | Operators can scrape metrics and view dashboards locally **without** disabling Actuator security |
| **Scope** | Compose `observability` profile: Prometheus + Grafana; secure scrape config; Grafana provisioning; required dashboards below; low-cardinality Micrometer meters |
| **Exclusions** | Remote long-term storage; PagerDuty; making `/actuator/prometheus` anonymously public; high-cardinality labels; using metrics as payment authority |
| **Backend** | Confirm prometheus samples; add observational counters/timers (e.g. rate_limit_rejections by **route template / outcome** only); **HTTP Basic scrape principal** for `/actuator/prometheus` (**U1**) — **without** `permitAll` / public flag |
| **Frontend** | None required |
| **Database** | None |
| **API** | Actuator only; no public contract change for commerce APIs |
| **Security** | See WP-C.1 scrape model |
| **Tests** | Actuator denied anonymously when public=false; authorized scrape (or in-network scrape principal) succeeds; sample series present |
| **Acceptance** | Compose docs; provisioned Grafana dashboards covering WP-C.2; cardinality rules documented |
| **Depends on** | None hard; benefits from WP-A rejection meters |

#### WP-C.1 Secure scrape model — locked (U1)

**Problem:** Today, with `ACTUATOR_PROMETHEUS_PUBLIC=false`, anonymous `GET /actuator/prometheus` returns **401**. Opening it publicly is **not** the Phase 8 solution.

**Locked approach:**

1. Prometheus scrapes `http://backend:8080/actuator/prometheus` on the **internal Docker network** using **HTTP Basic Auth**.  
2. Credentials supplied via **environment variables** (e.g. `ACTUATOR_PROMETHEUS_USERNAME` / `ACTUATOR_PROMETHEUS_PASSWORD` or equivalently named Compose env) — placeholders in `.env.example` only; **no** real secrets committed.  
3. Spring Security: authenticate scrape requests to prometheus (and keep other actuator endpoints as already designed); **do not** `permitAll` prometheus; **do not** set `ACTUATOR_PROMETHEUS_PUBLIC=true` for convenience.  
4. Host-published `:8080` must still require auth for prometheus — Docker-network-only trust is **not** sufficient alone.  
5. **Local profile note:** `application-local.yml` currently sets prometheus public for developer convenience — Phase 8 security review must **call out** that Docker/`observability` must **not** copy that shortcut; keep docker default false.  
6. Document username/password wiring and example `curl -u` / Prometheus `basic_auth` in the observability runbook.

#### WP-C.2 Required Grafana dashboards / panels

Provision at least one (or a small set of) dashboard(s) covering:

| Panel group | Signals (illustrative Micrometer/Actuator names) |
|-------------|--------------------------------------------------|
| HTTP traffic | Request **rate** by method/status/uri **template** (low cardinality) |
| Errors | **4xx / 5xx ratio** |
| Latency | **p95 / p99** (Timer percentiles or histogram quantiles) |
| JVM | Memory, GC, CPU |
| Dependencies | Redis health, Kafka health (from health / binder meters as available) |
| DB pool | **HikariCP** active/idle/pending |
| Rate limits | **rate-limit rejection** count (by route bucket name — not by order id) |

Optional observational business counters (e.g. orders_paid_total, payments_failed_total) **if** low-cardinality — **must never** drive order/payment status transitions; webhook/`PAID` authority unchanged.

#### WP-C.3 Cardinality & sensitivity

- **Forbidden labels:** `orderId`, `paymentIntentId`, `email`, raw IP (prefer coarse bucket name only), JWT/`sub` as free-form high-cardinality tag streams, cart ids.  
- **Allowed labels:** `uri` template / route name, `method`, `status`, `outcome` (`allowed`/`rejected`), `bucket` (`checkout_account`, etc.).  
- No secrets, tokens, or PAN in metric names/labels/help text.

---

### WP-D — Correlation IDs & OpenTelemetry

| | |
|--|--|
| **Objective** | End-to-end request correlation in logs; optional local tracing without ID conflicts or secret leakage |
| **Scope** | Correlation filter + MDC; validation; Micrometer Tracing + OTel exporter deps; env-gated OTLP |
| **Exclusions** | Always-on collector; heavy FE RUM product; logging secrets |
| **Backend** | Filter + logging pattern + deps + config per rules below |
| **Frontend** | Optional: echo/forward `X-Correlation-Id` — nice-to-have |
| **Database** | None |
| **API** | Additive response header `X-Correlation-Id` (and/or reflect trace context) |
| **Security** | P8-D13 redaction |
| **Tests** | Validation reject/ignore bad IDs; propagation; MDC cleanup; no conflict with Micrometer `traceparent` |
| **Acceptance** | Correlation visible in logs for sample request; OTel optional path documented |
| **Depends on** | WP-C helpful but not hard-required |

#### WP-D.1 Correlation / tracing rules (U3 + U5 locked)

**Single propagation strategy:**

| Mode | Canonical request id | Client-visible header | MDC |
|------|----------------------|----------------------|-----|
| Tracing **enabled** (Micrometer / W3C active) | Active **traceId** from W3C/`traceparent` context | Set `X-Correlation-Id` **equal to** that traceId (do not mint a second unrelated id) | Same value |
| Tracing **disabled** | Newly generated UUID (or equivalent) as `X-Correlation-Id` | `X-Correlation-Id` | Same value |

**Rules:**

1. **Validate** incoming `X-Correlation-Id` / `traceparent`: allowlist charset / W3C grammar; max length ≤ **128** for custom correlation id; `traceparent` per W3C.  
2. **U3:** Malformed or oversized incoming correlation IDs → **ignore** and generate/adopt a valid id. **Do not** return HTTP **400** solely because the correlation header is invalid.  
3. **U5:** Prefer the **active W3C/Micrometer trace context** when tracing is enabled; **never** create duplicate/conflicting trace ids for one request.  
4. When tracing is disabled, preserve a **stable** generated `X-Correlation-Id` for the request lifetime.  
5. If a valid incoming `X-Correlation-Id` is present and tracing is **disabled**, may reuse it after validation; if tracing is **enabled**, Micrometer/W3C context wins (incoming custom id must not fork a second identity).  
6. Put canonical id in **MDC**; return `X-Correlation-Id` on response.  
7. **Clear MDC in `finally`** after each request.  
8. **Never log:** JWTs, refresh tokens, Stripe `client_secret`, PAN, raw API secrets, webhook signing secrets.

---

### WP-E — Test hardening & regression

| | |
|--|--|
| **Objective** | Stabilize known flake; re-affirm Phase 1–7 suites; expand tests for Phase 8 controls |
| **Scope** | Fix CheckoutPage Vitest timeout flake; all §5.1 tests (incl. T9–T13 atomic dual-bucket); Surefire + Failsafe (Docker); credential-free Playwright; optional credentialed Stripe checklist |
| **Exclusions** | Weakening or deleting Phase 1–7 tests; claiming Stripe Live success |
| **Backend / Frontend** | Test-only preferred; production code only if required for genuine flake root cause |
| **Database** | None expected; **if** a migration appears necessary → stop and request approval |
| **API** | None |
| **Tests** | Full regression green + Phase 8 control suite |
| **Acceptance** | Document counts; Failsafe including Postgres ITs executed in this phase |
| **Depends on** | WP-A…D largely done (flake fix can start earlier) |

---

### WP-F — Docs, SLOs, Phase 8 exit

| | |
|--|--|
| **Objective** | Phase 8 exit package |
| **Scope** | SLO doc (local aspirational); observability runbook (including **secure scrape**); update SECURITY.md / README / TASK_TRACKER; `docs/PHASE8_COMPLETION_REPORT.md`; `docs/PHASE8_SECURITY_REVIEW.md` |
| **Exclusions** | Production SRE program |
| **Acceptance** | Completion report + green verification commands in §7 |
| **Depends on** | WP-A…E |

**Suggested order:** A ∥ B → C → D → E → F.

---

## 5. Testing & acceptance (Phase 8 controls)

### 5.1 Explicit tests required

| # | Test intent | Notes |
|---|-------------|-------|
| T1 | Rate limiting runs **before** any checkout/payment side effects | Assert no order/PI/stock mutation on 429 |
| T2 | Legitimate **idempotent checkout retries** succeed under normal budget | Same Idempotency-Key + account; dual-bucket still allows within WP-A.2 defaults |
| T3 | **Redis fail-open** | Atomic multi-bucket op / Redis error → request proceeds (warn logged); denylist semantics unchanged |
| T4 | **Stripe webhook not incorrectly blocked** | Webhook excluded from business API rate limiter (U2); signature + idempotent processing still pass; no spurious app 429 |
| T5 | **Actuator access denied** when public exposure disabled | Anonymous `/actuator/prometheus` → 401/403 with flag false |
| T6 | **CSP / security headers** without breaking SPA | Report-only CSP present; smoke that FE/API still load; HSTS not forced on HTTP |
| T7 | **Correlation ID** propagation + **MDC cleanup** | Valid id round-trip; invalid ignored; MDC empty after request |
| T8 | **Prometheus scrape authorization** | Scrape succeeds with configured scrape credentials; fails without — **not** via public flag |
| T9 | **Atomic allow:** account + IP both increment when request allowed | After success, both Redis counters advanced by 1 in the same window |
| T10 | **Account reject does not burn IP** | When account bucket would exceed budget → 429 and **IP counter unchanged** |
| T11 | **IP reject does not burn account** | When IP bucket would exceed budget → 429 and **account counter unchanged** |
| T12 | Legitimate requests still succeed under dual-bucket limits | Normal checkout/pay within budgets |
| T13 | Idempotent checkout retries still work under dual-bucket limits | Complements T2 with explicit dual-bucket focus |
| — | Preserve **all Phase 1–7** tests and money-path invariants | No weaken/delete |

### 5.2 Phase exit acceptance criteria

1. Sensitive APIs beyond auth rate-limited per §4 WP-A with documented keys/budgets, atomic dual-bucket semantics (WP-A.4 / P8-D15), and tests T1–T4 + T9–T13.  
2. Security review doc completed; CSP report-only; HSTS HTTPS-only; Actuator safe by default (T5–T6).  
3. Prometheus scrapes securely on Docker network; Grafana dashboards cover WP-C.2; cardinality rules held (T8).  
4. Correlation IDs validated; MDC cleared; optional OTel documented (T7).  
5. Vitest flake addressed; Surefire + Failsafe documented; Phase 1–7 tests not weakened.  
6. SLOs + observability runbook (secure scrape) published.  
7. `docs/PHASE8_COMPLETION_REPORT.md` written; tracker updated.  
8. **No** production-readiness claim; **no** Phase 9 started automatically; **no** migration without approval.

---

## 6. Invariants (must not break)

| Invariant | Phase 8 rule |
|-----------|--------------|
| Webhook sole `PAID` authority | No status shortcuts via metrics/admin; business metrics observational only |
| Checkout idempotency / pay PI reuse | Rate limit before side effects; 429 safe; retries within budget OK |
| Refund P7-D10 verify | Do not bypass provider retrieve |
| JWT / refresh model | No storage model change |
| ADMIN vs CUSTOMER shopping | Preserve blocks; admin refund limits do not replace RBAC |
| Flyway V1–V13 | No rewrite; **no new migration without approval** (P8-D7) |
| Fail-open Redis rate limit | Preserve unless approval to fail-closed (including atomic multi-bucket path) |
| Dual-bucket atomicity | Reject must not increment sibling under-budget counters (P8-D15) |
| Actuator non-public default | Do not open prometheus publicly for scrape convenience (P8-D10) |

If a Phase 8 design requires changing any of the above → **stop and request approval**.

---

## 7. Risks & dependencies

| Risk | Mitigation |
|------|------------|
| Over-aggressive rate limits break E2E/demo | Generous local defaults (§4 WP-A.2); documented env knobs |
| Idempotent retry counted as separate hits | Size budgets for FE retry; document; never key by Idempotency-Key as unbounded label |
| Non-atomic dual INCR burns sibling quota on 429 | Mandatory Lua (or equivalent) per WP-A.4; tests T9–T11 |
| Grafana port clash with frontend `:3000` | Grafana → `3001` (or similar) |
| OTel / Micrometer duplicate trace ids | Single ownership rule (P8-D5); tests T7 |
| CSP enforce breaks Stripe Elements | Report-only until checklist PASS |
| HSTS breaks local HTTP | HTTPS-only emission |
| Temptation to set `ACTUATOR_PROMETHEUS_PUBLIC=true` | P8-D10 + scrape credentials on Docker network |
| Metrics cardinality explosion | P8-D12; code review of meters |
| Redis fail-open during abuse | Document residual risk (SECURITY.md) |
| Scope creep into Phase 9 | Explicit out-of-scope list |

**Dependencies:** Phase 7 codebase; Docker Desktop for Compose observability + Failsafe; Node 22+ / JDK 21 / Maven as today.

---

## 8. Local verification commands (expected at exit)

```bash
# Backend
cd backend
mvn -B clean test
mvn -B verify                    # Docker Testcontainers

# Frontend
cd frontend
npm test
npm run build
npm run lint
npm audit
npm run test:e2e                 # credential-free; skips if app down / missing E2E_*

# Stack
# JWT_SECRET must be non-default for docker profile (session env)
docker compose -f infra/docker/docker-compose.yml --profile app up -d --build
docker compose -f infra/docker/docker-compose.yml --profile observability up -d

# Probes
curl -s localhost:8080/actuator/health/liveness
# Anonymous prometheus must NOT succeed when public flag false:
curl -s -o /dev/null -w "%{http_code}" localhost:8080/actuator/prometheus   # expect 401
# Authorized scrape (credentials per runbook) — expect 200 + jvm_ / http_server samples
curl -s localhost:9090/-/ready                 # Prometheus
# Grafana UI on configured host port (e.g. 3001)
```

Expected: suites green; health UP; Prometheus target **UP** via authenticated scrape; Grafana loads provisioned dashboards; completion report lists exact counts.

---

## 9. Resolved design decisions (U1–U6)

All former outstanding items are **resolved** for this plan. Status remains **PLANNED — AWAITING APPROVAL** of the full Phase 8 plan before implementation.

| ID | Decision | Resolution |
|----|----------|------------|
| **U1** | Prometheus scrape authentication | **Internal Docker-network HTTP Basic Auth**; credentials via **environment variables**. Do **not** expose `/actuator/prometheus` publicly or disable Actuator security for convenience. See WP-C.1. |
| **U2** | Stripe webhook rate limiting | **Do not** apply the ordinary business API rate limiter to Stripe webhooks. Preserve signature verification, idempotent processing, and safe Stripe retries. Document any infrastructure-level protection separately (not as app webhook RL). |
| **U3** | Invalid correlation IDs | **Ignore** malformed/oversized incoming correlation IDs and generate/adopt a valid id. **Do not** HTTP 400 an otherwise valid business request solely for a bad correlation header. |
| **U4** | CSP enforcement | Keep CSP in **report-only** initially. Enforcement remains **disabled** until SPA + `/api` proxy + Stripe Payment Element compatibility is verified per WP-B.1 promotion criterion; default Phase 8 exit stays report-only. |
| **U5** | Correlation ID and trace ID | **One strategy:** when tracing enabled, canonical id = active W3C/Micrometer **traceId** and `X-Correlation-Id` matches it (no duplicates/conflicts); when tracing disabled, stable generated `X-Correlation-Id`. See WP-D.1. |
| **U6** | Rate-limit budgets | **Locked local defaults** in WP-A.2: fixed-window (60s), account+IP keying, per-endpoint env overrides, generous for local/demo — **not** production traffic assumptions. Stripe webhook excluded (U2). Unchanged by P8-D15 atomic rule. |

### Remaining unresolved issues

**None** for U1–U6. Residual items that still require **plan approval** (not design ambiguity):

- Explicit user approval to **start Phase 8 implementation** (WP-A…F).  
- Exact Spring Security wiring details for Basic scrape user (implementation detail within U1).  
- Exact Compose env var names for scrape credentials (document at implementation; placeholders only).  
- Whether CSP promotion criterion is met in-phase (outcome of WP-B checklist — enforce still gated).  
- Exact Lua script source / Redis command names for WP-A.4 (implementation detail within P8-D15; behavior is locked).

---

## 10. Explicit non-goals confirmation

- No implementation in this planning revision.  
- No migrations executed or authored in this task.  
- No API/config/application/test/dependency/Docker/runtime changes in this task.  
- No Phase 9/10 work.

---

## 11. Approval gate

**Status: PLANNED — AWAITING APPROVAL.**

U1–U6 are resolved inside the plan; P8-D15 (atomic dual-bucket RL) is locked. Implementation of WP-A…F starts only after **explicit approval** of this plan (P8-D1…D15 + U1–U6).
