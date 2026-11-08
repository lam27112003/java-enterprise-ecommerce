# Phase 7 WP-A — Final Independent QA Report

**Date:** 2026-11-05  
**Reviewer role:** Independent QA after dependency remediation  
**Scope:** WP-A only (frontend foundation + approved dependency upgrades)  
**Recommendation:** **PASS** (with documented Low/Medium residuals; none block WP-B start)

---

## 1. Review scope

| In scope | Out of scope |
|----------|--------------|
| Frontend WP-A shell, auth client, guards, RR7 remediation | WP-B+ features |
| `package.json` / lockfile / `.npmrc` / audit | Backend code changes |
| Vitest / build / lint verification | Migrations, deploy, Live Stripe |
| Frontend ↔ existing auth API contract comparison | Implementing fixes (document + propose only) |

**No application fixes were applied during this review.**

---

## 2. Files inspected

### Dependencies / tooling
- `frontend/package.json`
- `frontend/package-lock.json` (lockfileVersion 3; root deps match package.json)
- `frontend/.npmrc`
- `frontend/vite.config.ts`
- `frontend/.env.example`
- `docs/PHASE7_WP_A_DEPENDENCY_REMEDIATION_REPORT.md` (cross-check)

### Auth / API
- `frontend/src/shared/lib/tokenStorage.ts`
- `frontend/src/shared/lib/apiClient.ts`
- `frontend/src/shared/lib/env.ts`
- `frontend/src/shared/lib/errors.ts`
- `frontend/src/shared/lib/safePath.ts`
- `frontend/src/shared/types/auth.ts`
- `frontend/src/shared/types/api.ts`
- `frontend/src/features/auth/authApi.ts`
- `frontend/src/features/auth/AuthContext.tsx`
- `frontend/src/features/auth/pages/LoginPage.tsx`
- `frontend/src/features/auth/pages/RegisterPage.tsx`
- `frontend/src/features/auth/components/{CustomerRoute,AdminRoute,RequireAuth}.tsx`

### Routing / UI shell
- `frontend/src/app/{providers,router}.tsx`
- `frontend/src/app/layouts/{CustomerLayout,AdminLayout,AuthLayout}.tsx`
- `frontend/src/pages/{HomePage,NotFoundPage,UnauthorizedPage,PlaceholderPage}.tsx`
- `frontend/src/App.tsx`, `frontend/src/main.tsx`

### Tests
- `App.test.tsx`, `apiClient.test.ts`, `tokenStorage.test.ts`, `env.test.ts`, `guards.test.tsx`
- `safePath.test.ts`, `router.smoke.test.tsx`

### Backend contract (read-only)
- `AuthController`, `LoginRequest`, `RegisterRequest`, `RefreshRequest`, `LogoutRequest`
- `AuthTokensResponse`, `UserProfileResponse`
- `AuthService` role mapping (`role.getCode().name()` → `ADMIN` / `CUSTOMER`)
- `SecurityConfig` public matchers for `/auth/register|login|refresh`

---

## 3. Commands and actual results

Executed in `frontend/` on 2026-11-05:

| Command | Result |
|---------|--------|
| `npm ls react react-dom react-router react-router-dom vite vitest @vitest/mocker tinypool` | react **18.3.1**, react-dom **18.3.1**, react-router **7.18.5**, react-router-dom **7.18.5**, vite **6.4.5**, vitest **4.1.12**, @vitest/mocker **4.1.12**; **tinypool absent** |
| `npm audit` | **`found 0 vulnerabilities`** |
| `npm test` | **22 passed** / 7 files |
| `npm run build` | **OK** (tsc + vite 6.4.5) |
| `npm run lint` | **OK** (tsc --noEmit) |

Versions match `docs/PHASE7_WP_A_DEPENDENCY_REMEDIATION_REPORT.md`.

---

## 4. Dependency integrity

| Check | Result |
|-------|--------|
| `package.json` ↔ lockfile root deps | Consistent (`react`/`react-dom`/`react-router-dom`/`vite`/`vitest` exact pins) |
| No `overrides` / `resolutions` in package.json | Confirmed |
| Duplicate react-router majors | Not observed; single `7.18.5` tree |
| `npm audit` | Clean |

### `.npmrc` — `legacy-peer-deps=true`

**Impact (verified intent from file comments + remediation report):**

- Tells npm to **skip strict peer-dependency conflict failures** when building the install tree.
- Was required because npm **10.6.0** + Node **24** arborist crashed (`edgesOut` null) resolving Vitest 4 optional peers under default peer resolution.
- **Does not** change declared package versions; **does not** suppress `npm audit` advisories.
- **Residual risk:** peer mismatches can be installed silently; future upgrades should re-test install **without** the flag when arborist is fixed, or document continued need.

**Not** an npm `overrides` block. Acceptable for WP-A with documentation.

---

## 5. Findings

### Critical
*None.*

### High
*None.*

### Medium

| ID | Finding | Evidence | Proposed fix (not applied) |
|----|---------|----------|----------------------------|
| **M1** | Auth UI can remain “authenticated” if access JWT is rejected with **401** and **no refresh token** is present | `apiClient` clears storage on final 401 but does not notify `AuthContext`; only the refresh-handler path updates `status`/`user` | On final 401 after failed/skipped refresh, invoke a registered `onUnauthorized()` that sets AuthContext to `anonymous` (or clear user in the same place `clearTokens` runs) |
| **M2** | `isSafeInternalPath` does not URI-decode before checking backslashes | Path like `/%5Cevil.example` may pass the literal `\` check | Decode (`decodeURIComponent` safely) then re-run checks; expand unit tests |

### Low

| ID | Finding | Evidence | Proposed fix (not applied) |
|----|---------|----------|----------------------------|
| **L1** | Guard redirect `state.from` uses `location.pathname` only | Query/hash lost on return after login | Pass `pathname + search + hash` (still through `isSafeInternalPath`) |
| **L2** | `legacy-peer-deps` masks peer warnings | `.npmrc` | Revisit when npm arborist supports Vitest 4 without the flag; CI note in README |
| **L3** | No live browser E2E against running backend for login/refresh | WP-A unit/smoke only | Covered by Phase 7 WP-G Playwright plan |
| **L4** | `/account` is `RequireAuth` (ADMIN or CUSTOMER), not `CustomerRoute` | `router.tsx` | Acceptable for WP-A profile shell; refine in WP-B if admin should not use customer account page |

---

## 6. Authentication and authorization review

| Requirement | Verdict | Notes |
|-------------|---------|-------|
| Access token memory-only | **Pass** | Module variable; not written to `sessionStorage`/`localStorage` |
| Refresh in `sessionStorage` | **Pass** | Key `ee.auth.refreshToken` |
| Refresh JSON body `{ refreshToken }` | **Pass** | Matches `RefreshRequest`; `POST /auth/refresh`; `skipAuth` + `skipRefresh` |
| Concurrent 401 → single refresh | **Pass** | `refreshInFlight` single-flight; covered by unit test |
| Failed refresh clears tokens | **Pass** | `refreshTokensWithBody` catch → `clearTokens`; handler sets `anonymous` |
| Logout clears both tokens | **Pass** | `authApi.logout` `finally { clearTokens() }` + AuthContext anonymous |
| ADMIN blocked from cart/checkout/orders UI | **Pass** | Routes under `CustomerRoute`; layout hides Cart for non-customer |
| CUSTOMER blocked from `/admin` | **Pass** | `AdminRoute` → `/unauthorized` |
| Backend still enforces commerce denial for ADMIN | **N/A FE / Pass BE** | Existing Phase 2–6 security; FE mirrors P7-D2 |

**401 + refresh present:** refresh handler is `refreshSession`, so failed refresh updates React auth state — verified by code path inspection (not only storage clear).

---

## 7. React Router 7 compatibility review

| Area | Verdict |
|------|---------|
| `BrowserRouter` + nested `Routes`/`Route`/`Outlet` | Compatible; builds clean |
| Obsolete `future.v7_*` flags | Removed (correct for RR7) |
| No v5 `Switch` / `Redirect` / `useHistory` | None found |
| Login redirect + `isSafeInternalPath` | Present; residual **M2** encoding edge |
| Unknown routes | `path="*"` → `NotFoundPage` |
| Unauthorized | Dedicated page + guard redirects |
| NavLink / Link | Used in layouts; RR7-compatible |

---

## 8. Backend contract compatibility

| Frontend | Backend | Match? |
|----------|---------|--------|
| `POST /api/v1/auth/login` `{ email, password }` | `LoginRequest` | **Yes** |
| `POST /api/v1/auth/register` `{ email, password, fullName }` | `RegisterRequest` | **Yes** (JSON names; order irrelevant) |
| `POST /api/v1/auth/refresh` `{ refreshToken }` | `RefreshRequest` | **Yes** |
| `POST /api/v1/auth/logout` Bearer + optional `{ refreshToken }` | `LogoutRequest` + Bearer extract | **Yes** |
| `GET /api/v1/auth/me` | `UserProfileResponse` (`id`, `email`, `fullName`, `status`, `roles`, `createdAt`) | **Yes** (FE maps `id` → `userId` for session user) |
| Token envelope `ApiResponse.data` → `AuthTokensResponse` fields | camelCase access/refresh/roles | **Yes** |
| Roles `ADMIN` / `CUSTOMER` (no `ROLE_` prefix in body) | `role.getCode().name()` | **Yes**; FE also accepts `ROLE_*` defensively |
| Default API base `/api/v1` + Vite proxy `/api` → `:8080` | Controllers under `/api/v1` | **Yes** |

**No blocking contract mismatches found for WP-A auth.**

---

## 9. Tests inventory

### Original WP-A suite (17) — still present

| File | Count |
|------|-------|
| `App.test.tsx` | 1 |
| `env.test.ts` | 3 |
| `tokenStorage.test.ts` | 3 |
| `apiClient.test.ts` | 4 |
| `guards.test.tsx` | 6 |
| **Total** | **17** |

### Added during remediation (5) — meaningful

| File | Count | Purpose |
|------|-------|---------|
| `safePath.test.ts` | 2 | Open-redirect rejection / allowlist |
| `router.smoke.test.tsx` | 3 | Login/register render; customer→login; customer↛admin |
| **Total** | **5** | |

**Grand total:** 22 — all passed. No tests weakened or removed.

---

## 10. Remaining manual QA (before / during WP-B)

1. Live login/register against local backend + Mailhog/DB seed admin.
2. Confirm refresh rotation across tab reload (sessionStorage refresh + memory access re-bootstrap).
3. Confirm ADMIN account cannot call cart/order APIs (browser network) even if URL forced.
4. Visual pass of layouts at mobile width (WP-A shell only).
5. Optional: try install without `legacy-peer-deps` on newer npm when available.

---

## 11. Final recommendation

### **PASS**

WP-A meets exit criteria for foundation + dependency remediation:

- Audit clean; pinned versions as approved  
- Auth storage / body refresh / single-flight verified  
- RR7 routes and guards coherent with P7-D2  
- Backend auth contracts aligned  
- Automated suite green (17 original + 5 remediation)

Medium items **M1**/**M2** are hardening follow-ups (prefer before or early in WP-B), not release blockers for starting WP-B after product owner approval.

**Do not start WP-B until the product owner explicitly approves.** This report does not authorize WP-B by itself.
