# Task Tracker — Java Enterprise E-commerce Platform

**Last updated:** 2026-11-06  
**Current phase:** Phase 8 — Hardening & Observability (**COMPLETE** for local portfolio; Failsafe Docker ITs pending host Docker; Phase 9 not started)  
**Locked stack:** Java 21 + Spring Boot **3.5.16** + Stripe test mode  
**Project path:** `C:\Users\84919\source\repos\java-enterprise-ecommerce`  
**Rule:** Check items only when done. Do not mark tests green unless executed.

---

## Phase 0 — Planning & Approval

- [x] Inspect existing workspace
- [x] Verify Java / Spring Boot / dependency compatibility notes
- [x] Draft complete system architecture
- [x] Propose folder structure
- [x] Design database ERD and schema summary
- [x] List API endpoints
- [x] Define security architecture
- [x] Define development roadmap
- [x] Define testing strategy
- [x] Define Docker architecture
- [x] Define AWS deployment architecture
- [x] Risk assessment + assumptions
- [x] Create `DEVELOPMENT_PLAN.md`
- [x] Create `TASK_TRACKER.md`
- [x] User decision: Spring Boot **3.5.x** (pin **3.5.16**)
- [x] Verify Boot 3.5.16 BOM: Security, Data JPA, Kafka, Redis/Lettuce, Flyway, Testcontainers
- [x] Pin maintained libs: springdoc **2.10.0**, JJWT **0.13.0**, stripe-java **34.0.0**
- [x] User confirmation: Stripe sandbox OK
- [x] User confirmation: project path OK
- [x] Keep modular monolith + 11-phase roadmap
- [x] User approval of locked technology stack (§1 in DEVELOPMENT_PLAN)
- [x] **User approval to start Phase 1**

---

## Phase 1 — Foundation

- [x] Initialize Git repository
- [x] Create backend Maven project (Java 21, Spring Boot **3.5.16**)
- [x] Initialize React + TypeScript + Vite + Tailwind frontend
- [x] Base packages: `common`, `config`, `security` stubs
- [x] `application.yml` profiles: local, docker, test, prod (+ standalone for offline smoke)
- [x] Configure PostgreSQL datasource (local/docker/prod)
- [x] Configure Flyway migrations (`V1__platform_foundation.sql`)
- [x] Configure Redis (Lettuce / conditional beans)
- [x] Configure Kafka (topics + conditional enable)
- [x] Global exception handling + problem responses
- [x] Actuator health/readiness/liveness + Prometheus endpoint exposure
- [x] springdoc OpenAPI baseline
- [x] Dockerfiles (backend + frontend)
- [x] `docker-compose.yml`: PostgreSQL, Redis, Kafka (KRaft), Mailhog (+ app profile)
- [x] `.env.example` + `.gitignore`
- [x] GitHub Actions CI skeleton (build/test)
- [x] README quickstart (draft)
- [x] Add initial automated tests (backend + frontend)
- [x] Run backend tests (`mvn test` → 5 passed)
- [x] Run frontend tests (`npm test` → 1 passed) + production build
- [x] Run application smoke (`standalone` profile) and verify HTTP endpoints
- [ ] Verify: backend starts against Compose infra (**blocked: Docker not installed on this machine**)
- [x] **User approval to start Phase 2**

---

## Phase 2 — Authentication & User

- [x] Entities: User, Role, RefreshToken (+ RoleCode / UserStatus)
- [x] Flyway migration `V2__auth_identity.sql` (users, roles, user_roles, refresh_tokens)
- [x] Registration / login / logout / refresh / me
- [x] JWT access tokens + opaque hashed refresh rotation + reuse detection
- [x] BCrypt password hashing + strong password validation
- [x] RBAC (`ADMIN`, `CUSTOMER`) + security filter chain / entry point / access denied
- [x] CORS configuration + privilege escalation protection (register → CUSTOMER only)
- [x] Probe endpoints `/api/v1/admin/**` and `/api/v1/customer/**`
- [ ] Password reset request/confirm (email via Mailhog) — **deferred** (not in Phase 2 API scope)
- [ ] User address book APIs — **deferred** to User module follow-up
- [x] Unit tests: JWT, password, auth service, refresh rotation/reuse
- [x] Integration tests: auth flows, RBAC, expired/revoked tokens
- [x] Execute tests; fix failures (`mvn test` → **18 passed** pre-remediation; **25 passed** post-remediation)
- [x] Phase 2 security & code quality audit (read-only) → `docs/PHASE2_SECURITY_AUDIT.md`
- [x] Remediate High findings F-01..F-04 + Medium F-05..F-07 → `docs/PHASE2_SECURITY_REMEDIATION_REPORT.md`
- [x] Access-token denylist (`jti` / Redis + in-memory) + logout revocation
- [x] DB-backed status/roles in JWT filter (+ `SUSPENDED`)
- [x] Pessimistic refresh rotation + concurrent refresh tests
- [x] Auth rate limiting (login/register/refresh) + 429 tests
- [x] Fail-fast JWT secrets (prod/docker); restrict actuator/docs by profile
- [x] Update `docs/SECURITY.md`, audit, completion report, tracker
- [x] Final independent verification (audit-only) → `docs/PHASE2_FINAL_VERIFICATION.md` (`mvn -B clean test` → 25/25)
- [x] Failsafe wiring (`*IT.java`) + Redis/Postgres Testcontainers ITs authored
- [x] Expanded rate-limit tests (login/register/refresh + window recovery)
- [x] Production JWT negative tests + actuator exposure test
- [x] Surefire re-run: `mvn -B clean test` → **33 passed**
- [x] Failsafe Redis/Postgres ITs green — Docker Desktop verified; `PostgresAuthIT` 4/4 + `RedisSecurityIT` 6/6 → `docs/PHASE2_INTEGRATION_VERIFICATION.md`
- [x] Fixed Redis bean selection race (`AuthInfrastructureConfig` via `ObjectProvider`)
- [x] **User approval to start Phase 3** (2026-11-04)

---

## Phase 3 — Category & Product Catalog

- [x] Category CRUD (admin) + public tree/list
- [x] Product CRUD (admin) + public detail
- [x] Product images metadata + local/S3 storage abstraction
- [x] Search, filter, sort, pagination
- [x] Redis cache-aside for product/category reads + invalidation (fail-open)
- [x] Indexes for catalog queries (`V4__catalog.sql`)
- [x] Avoid N+1 (entity graphs / fetch joins / `@BatchSize`)
- [x] Trusted-proxy gate for `X-Forwarded-For` / `X-Real-IP` (`ClientIpResolver`)
- [x] Unit + repository + controller tests (`CatalogIntegrationTest`, `SlugUtilsTest`, `ClientIpResolverTest`)
- [x] Testcontainers ITs: `CatalogPostgresIT`, `CatalogRedisCacheIT`
- [x] Docs: `docs/API_DOCUMENTATION.md`, `docs/PHASE3_COMPLETION_REPORT.md`, `docs/SECURITY.md`
- [x] Execute tests; fix failures — Surefire **38/38**, Failsafe **12/12** (2026-11-04)
- [x] **User approval to start Phase 4** (2026-11-04)

---

## Phase 4 — Inventory & Shopping Cart

- [x] Inventory entity with versioning + constraints (`V5__inventory_cart.sql`)
- [x] Admin stock adjust API (delta + reason + audit)
- [x] Reserve / release / commit stock operations (internal only)
- [x] Auto-create inventory (0/0) on product create + V5 backfill
- [x] Cart APIs (add/update/remove/clear/totals) — CUSTOMER only, no guest
- [x] Availability validation on cart mutations (no reserve-on-add)
- [x] Concurrent purchase / oversell prevention tests (`InventoryConcurrencyIT`)
- [x] Docs: `PHASE4_DECISION_LOCK.md`, `PHASE4_COMPLETION_REPORT.md`, API/SECURITY updates
- [x] Execute tests — Surefire **42/42**, Failsafe **16/16** (2026-11-04)
- [x] **Phase 5 planning + decision lock complete** (P5-D1…P5-D11)
- [x] **User approval to start Phase 5.1** (Orders DB & domain foundation)

---

## Phase 5 — Orders & Promotions

- [x] Readiness plan + decision locks (P5-D1…P5-D11) documented
- [x] Orders DB & domain foundation (`V6__orders_foundation.sql`)
- [x] Promotions persistence + admin CRUD + customer validate / cart promo (`V7__…`)
- [x] Checkout: catalog prices, reserve, promo usage, Idempotency-Key, clear cart
- [x] Inventory reserve/release/commit join caller TX (P5-D9)
- [x] Order status: PENDING_PAYMENT → CANCELLED | PAYMENT_FAILED only (no mark-paid)
- [x] Customer order history/detail/cancel; admin list/status
- [x] Exactly-once inventory release + promo restore on cancel / PAYMENT_FAILED
- [x] Integration + concurrency tests (checkout, idempotency, oversell, promo limits)
- [x] Docs: API, SECURITY, `PHASE5_COMPLETION_REPORT.md`
- [x] Verification — Surefire **54/54**, Failsafe **19/19** (2026-11-04)
- [x] **User approval to start Phase 6**

---

## Phase 6 — Payment, Kafka Events, Notifications

- [x] Stripe Test Mode config (test keys via env only; mock provider in tests)
- [x] Separate `POST /api/v1/orders/{orderId}/pay` after checkout (not inside checkout)
- [x] Duplicate pay returns the existing active PaymentIntent
- [x] No re-pay on `PAYMENT_FAILED` (new checkout required)
- [x] Webhook signature verification + success/failure/cancel handling
- [x] Late success on `CANCELLED` / `PAYMENT_FAILED` never sets `PAID` or commits inventory; one refund workflow
- [x] Cancel attempts PaymentIntent cancel after commit; `CANCEL_REQUESTED` until confirmed
- [x] Never store PAN/CVV; do not persist `client_secret`
- [x] Kafka topics + producer configuration
- [x] Publish domain events via **required** transactional outbox
- [x] Consumer idempotency (`processed_events`) + outbox failure status after repeated dispatch errors
- [x] Refund worker via outbox (`REFUND_SUBMITTED` is not `REFUNDED`)
- [x] Notification module: email templates + Mailhog
- [x] Order paid / payment failed emails
- [x] Payment + outbox + notification tests
- [x] Execute tests; fix failures — Surefire **61/61**, Failsafe **27/27** (2026-11-05 hardening)
- [x] Required transactional outbox + refund worker
- [x] `docs/PHASE6_COMPLETION_REPORT.md`
- [x] Hardening: one open payment per order (V9 + PG V10 partial unique); concurrent pay; PaymentPostgresIT + PaymentOutboxKafkaIT; safe refund reconciliation service

Shipping status APIs stay in Phase 7. No reconciliation UI in Phase 6.  
**Phase 6 complete (hardened).**

---

## Phase 7 — Administration & Frontend

- [x] Implementation plan written — `docs/PHASE7_IMPLEMENTATION_PLAN.md` (2026-11-05)
- [x] Plan revised after security/architecture review — P7-D1…D10 locked
- [x] Final planning review — payment-period revenue timestamps (V13), concurrent last-ADMIN lock, Stripe Refund PI/Charge verify; P7-D11…D15 lock-on-approval
- [x] **User approved final plan (P7-D1…D15)** — implementation authorized
- [x] **WP-A: Frontend foundation** — router, layouts, API client (Bearer + JSON body refresh, single-flight), AuthContext, Customer/Admin/RequireAuth guards, login/register pages, `.env.example` (`VITE_API_BASE`, `VITE_STRIPE_PUBLISHABLE_KEY`); Vitest 17 passed; `npm run build` green
- [x] **WP-A dependency security assessment** — `docs/PHASE7_WP_A_DEPENDENCY_SECURITY_REVIEW.md`
- [x] **WP-A dependency remediation** — `react-router-dom@7.18.5`, `vitest@4.1.12`, React/Vite pinned; `npm audit` **0**; tests **22** passed; build/lint green — `docs/PHASE7_WP_A_DEPENDENCY_REMEDIATION_REPORT.md`
- [x] **WP-A final independent QA** — **PASS** (`docs/PHASE7_WP_A_FINAL_QA_REPORT.md`); residuals M1/M2 noted
- [x] **WP-A residual remediation (M1/M2)** — AuthContext sync on terminal 401; hardened `isSafeInternalPath`; tests **30** passed; `docs/PHASE7_WP_A_RESIDUAL_REMEDIATION_REPORT.md`
- [x] **WP-B: Customer storefront** — catalog list/search/filter/sort/pagination, product detail, CUSTOMER cart (add/update/remove), account (`/auth/me`); Vitest **49** passed; build/lint green; `npm audit` **0** — `docs/PHASE7_WP_B_COMPLETION_REPORT.md`
- [x] **WP-C implementation plan** — checkout, Idempotency-Key, `/pay`, Stripe Elements Test Mode, polling, orders/payment history; no new backend APIs — `docs/PHASE7_WP_C_IMPLEMENTATION_PLAN.md` (2026-11-05)
- [x] **Phase 7 execution workflow revised** — after WP-C verification, WP-D…G continue automatically (not separate approval gates); Phase 8 blocked until Phase 7 exit + `docs/PHASE7_FINAL_COMPLETION_REPORT.md` — plan §10.1 (2026-11-05)
- [x] **WP-C: Checkout + Stripe Elements** — shipping/promo checkout, Idempotency-Key, `/pay` + Payment Element, polling, orders/cancel/resume — `docs/PHASE7_WP_C_COMPLETION_REPORT.md`
- [x] **WP-D: Admin dashboard & management** — KPIs (payment-period), products/categories/inventory/promos, users status APIs + UI (no roles); V13 timestamps
- [x] **WP-E: Shipping** — V11 fulfillment fields + `order_status_history`; admin transitions; customer tracking display
- [x] **WP-F: Refund reconciliation** — provider-verified confirm, reopen same key, append-only audits, admin UI (P7-D10)
- [x] **WP-G: Integration & E2E** — Playwright local smokes, API/SECURITY docs, Compose profile documented; final report
- [x] Admin user list/search/status APIs (self-disable + last-ADMIN protections)
- [x] Dashboard summary analytics API (precise KPI formulas in plan §7)
- [x] Auth pages (login/register) — WP-A shell (full account profile in WP-B)
- [x] Catalog browsing + product detail — WP-B
- [x] Cart UI — WP-B; checkout/pay — WP-C
- [x] Order history UI — WP-C
- [x] Admin console (products, orders, inventory, promos, shipping, refunds, users)
- [x] API client with JWT refresh rotation (JSON body; not cookies) — WP-A
- [x] Frontend Dockerfile + Nginx proxy *(wired in compose profile `app`)*
- [x] Compose full stack definition (backend + frontend under profile `app`)
- [x] Playwright critical customer + admin smokes (local; CI deferred P7-D15)
- [x] **`docs/PHASE7_FINAL_COMPLETION_REPORT.md`** — Phase 7 exit artifact (2026-11-05)
- [x] **Phase 7 final acceptance verification (no code changes)** — **ACCEPT WITH LIMITATIONS** — `docs/PHASE7_FINAL_ACCEPTANCE_REPORT.md`
- [x] **Phase 7 closed for local portfolio delivery** (2026-11-05) — Compose healthy (session JWT_SECRET); liveness UP; FE 200; nginx API 200; Flyway V13; **not** production-certified; Phase 8 not started

**Phase 7 complete (local portfolio acceptance).** Preserved results: FE Vitest **68** (one full-suite flake noted); Playwright **4** passed / **2** skipped (credentials); BE Surefire **66**; Failsafe **30** (incl. Phase7 IT **3**, prior run); `npm audit` **0**; Compose re-verify **PASS**. Credentialed Stripe/admin E2E **NOT VERIFIED**. Playwright CI deferred (P7-D15). **Do not start Phase 8** without separate planning/approval.

---

## Phase 8 — Hardening & Observability

- [x] **Implementation plan approved** — P8-D1…D15 + U1–U6 (2026-11-06)
- [x] WP-A: Atomic account+IP Redis Lua rate limits (checkout/pay/admin refund; webhook excluded; fail-open)
- [x] WP-B: Security headers (CSP report-only; HTTPS-only HSTS) + `docs/PHASE8_SECURITY_REVIEW.md`
- [x] WP-C: Prometheus Basic Auth scrape + Compose `observability` (Prometheus/Grafana dashboards)
- [x] WP-D: Correlation IDs + Micrometer Tracing/OTel deps (env-gated)
- [x] WP-E: Surefire **82** green; Vitest **68** green; CheckoutPage flake mitigation; Failsafe **pending Docker host**
- [x] WP-F: SLOs, observability runbook, completion report
- [x] Redis-backed rate limiting expanded beyond auth
- [x] Security review pass (local portfolio)
- [x] Prometheus metrics endpoint (Boot 3.5 `management.prometheus.metrics.export.enabled`)
- [x] Grafana dashboards provisioned
- [x] OpenTelemetry instrumentation (optional / env-gated)
- [x] Structured logging correlation IDs
- [x] Phase 8 control tests (dual-bucket, headers, actuator Basic Auth, correlation)
- [ ] Re-run Failsafe when Docker Desktop available (`mvn -B verify`)
- [x] `docs/PHASE8_COMPLETION_REPORT.md`

**Do not start Phase 9 automatically.**

---

## Phase 9 — Kubernetes & Helm

- [ ] Deployments + Services for backend/frontend
- [ ] ConfigMaps + Secret templates (no real secrets)
- [ ] Ingress + health probes
- [ ] Resource requests/limits
- [ ] Helm chart + values
- [ ] Local kind/minikube install guide + verify
- [ ] Update `docs/KUBERNETES_GUIDE.md`

---

## Phase 10 — AWS Packaging (no spend without approval)

- [ ] Document EKS + RDS + S3 + IAM + Secrets Manager + CloudWatch architecture
- [ ] IAM policy templates
- [ ] Deployment scripts/stubs (explicitly non-destructive by default)
- [ ] `docs/AWS_DEPLOYMENT.md` complete
- [ ] **Explicit user approval before any paid AWS resource creation**

---

## Phase 11 — Documentation & Release Readiness

- [ ] `README.md` final
- [ ] `docs/PROJECT_OVERVIEW.md`
- [ ] `docs/ARCHITECTURE.md`
- [ ] `docs/DATABASE_DESIGN.md`
- [ ] `docs/API_DOCUMENTATION.md`
- [ ] `docs/SECURITY.md`
- [ ] `docs/TESTING.md`
- [ ] `docs/DOCKER_GUIDE.md`
- [ ] `docs/KUBERNETES_GUIDE.md`
- [ ] `docs/AWS_DEPLOYMENT.md`
- [ ] `docs/DEVELOPMENT_GUIDE.md`
- [ ] Sample seed data script
- [ ] Final checklist vs original requirements
- [ ] Tag/version readiness notes

---

## Blockers / Decisions Log

| Date | Item | Status |
|------|------|--------|
| 2026-11-04 | Spring Boot 3.5.x vs 4.1.x | **Confirmed: 3.5.16** |
| 2026-11-04 | Stripe as payment sandbox | **Confirmed** |
| 2026-11-04 | Project root path | **Confirmed** |
| 2026-11-04 | Ignore existing `C:\Users\84919\ecommerce` PHP stubs | Assumed yes |
| 2026-11-04 | Locked BOM + third-party versions | **Approved** |
| 2026-11-04 | Start Phase 1 implementation | **Approved / completed** |
| 2026-11-04 | Docker Desktop not installed | Compose files ready; full infra smoke deferred |
| 2026-11-04 | Start Phase 2 | **Approved / completed** |
| 2026-11-04 | Password reset + addresses | Deferred beyond core Phase 2 auth APIs |
| 2026-11-04 | Phase 2 security audit | **Completed** — see `docs/PHASE2_SECURITY_AUDIT.md` (no code changes during audit) |
| 2026-11-04 | Remediate audit High/Medium findings | **Completed** — F-01..F-07; report `docs/PHASE2_SECURITY_REMEDIATION_REPORT.md` |
| 2026-11-04 | Start Phase 3 | **Blocked** until explicit user approval |
| 2026-11-04 | Docker for Testcontainers | **Available** — Docker Desktop 4.98.0 / Engine 29.9.2; Failsafe green |

---

## Test Execution Log

| Date | Phase | Command | Result | Notes |
|------|-------|---------|--------|-------|
| 2026-11-04 | 1 | `mvn -B test` (backend) | **PASS** — Tests run: 5, Failures: 0 | ApiResponse, PlatformController, EcommerceApplication, PasswordEncoder |
| 2026-11-04 | 1 | `npm test` (frontend) | **PASS** — 1 test | App hero + platform info mock |
| 2026-11-04 | 1 | `npm run build` (frontend) | **PASS** | Vite production build |
| 2026-11-04 | 1 | `mvn -B -DskipTests package` | **PASS** | JAR built |
| 2026-11-04 | 1 | `spring-boot:run` profile `standalone` | **PASS** | `/api/v1/platform/info`, health, liveness, readiness, OpenAPI 200 |
| 2026-11-04 | 1 | `docker compose up` | **NOT RUN** | Docker binary not available on host |
| 2026-11-04 | 2 | `mvn -B test` (backend) | **PASS** — Tests run: **18**, Failures: 0 | Auth unit + integration + prior foundation tests |
| 2026-11-04 | 2-audit | `mvn -B test` (re-run) | **PASS** — 18/18 | Confirmed during security audit |
| 2026-11-04 | 2-audit | Concurrent refresh ×8 (live standalone :8087) | **1×200 / 7×401** | Race not multi-success in this run; code risk remains (F-03) |
| 2026-11-04 | 2-audit | Post-logout access token `/auth/me` | **200 (still usable)** | Confirms F-01 |
| 2026-11-04 | 2-audit | Post-logout refresh | **401** | Revocation works for refresh tokens |
| 2026-11-04 | 2-remediation | `mvn -B test` (backend) | **PASS** — Tests run: **25**, Failures: 0 | Includes SecurityRemediationTest (6) + RateLimitTest (1) + prior 18 |
| 2026-11-04 | 2-final-verify | `mvn -B clean test` (backend) | **PASS** — **25**/25, Skipped: 0 | Audit-only; H2 + in-memory Redis; Redis/PG Testcontainers not run; see `PHASE2_FINAL_VERIFICATION.md` |
| 2026-11-04 | 2-it-verify | `mvn -B clean test` | **PASS** — **33**/33 | Rate-limit×4, prod JWT×4, actuator×1 + prior |
| 2026-11-04 | 2-it-verify | `mvn -B failsafe:integration-test failsafe:verify` | **FAIL** — 2 errors, 0 skipped | Pre-Docker; environment missing |
| 2026-11-04 | 2-it-verify-rerun | `mvn -B clean test` | **PASS** — **33**/33 | After Docker install + Redis bean fix |
| 2026-11-04 | 2-it-verify-rerun | `mvn -B failsafe:integration-test failsafe:verify` | **PASS** — **10**/10 (PG 4 + Redis 6) | Real Postgres 16 + Redis 7.2 Testcontainers; see `PHASE2_INTEGRATION_VERIFICATION.md` |
