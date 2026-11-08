# Phase 7 WP-A — Dependency Remediation Report

**Date:** 2026-11-05  
**Scope:** Approved frontend security upgrades only (`react-router-dom@7.18.5`, `vitest@4.1.12`)  
**Status:** Complete — verified with `npm audit` **0 vulnerabilities**  
**WP-B:** Not started

---

## 1. Actual installed versions

Verified via `npm ls react react-dom react-router react-router-dom vite vitest @vitest/mocker tinypool`:

| Package | Target | Resolved | Notes |
|---------|--------|----------|-------|
| `react` | 18.3.1 | **18.3.1** | Pinned exact in `package.json` |
| `react-dom` | 18.3.1 | **18.3.1** | Pinned exact |
| `react-router-dom` | 7.18.5 | **7.18.5** | Direct dependency |
| `react-router` | (transitive) | **7.18.5** | Via `react-router-dom` |
| `vite` | 6.4.5 | **6.4.5** | Pinned exact |
| `vitest` | 4.1.12 | **4.1.12** | Direct devDependency |
| `@vitest/mocker` | (transitive) | **4.1.12** | Via vitest |
| `tinypool` | absent / ≥2.1.2 | **absent** | `npm ls tinypool` → empty tree |

---

## 2. Package files changed

| File | Change |
|------|--------|
| `frontend/package.json` | `react-router-dom` → `7.18.5`; `vitest` → `4.1.12`; pin `react`/`react-dom`/`vite`; add `@testing-library/dom` |
| `frontend/package-lock.json` | Regenerated for the new tree |
| `frontend/.npmrc` | `legacy-peer-deps=true` (install workaround; **not** a package override) |

### Application / test compatibility fixes

| File | Why |
|------|-----|
| `frontend/src/app/providers.tsx` | Removed obsolete `future={{ v7_* }}` props — invalid on React Router 7 types (flags are default) |
| `frontend/src/features/auth/components/guards.test.tsx` | Same `future` prop removal on `MemoryRouter` |
| `frontend/src/shared/lib/safePath.ts` | Defense-in-depth for login `from` redirect (GHSA-wrjc class) |
| `frontend/src/features/auth/pages/LoginPage.tsx` | Use `isSafeInternalPath(from)` before `navigate` |
| `frontend/src/shared/lib/safePath.test.ts` | Unit coverage for safe/unsafe paths |
| `frontend/src/app/router.smoke.test.tsx` | RR7 smoke: login/register render, guard redirects |

**Not used:** `npm audit fix --force`, npm `overrides`.

---

## 3. Install notes (npm arborist)

Plain `npm install vitest@4.1.12` on **npm 10.6.0 / Node v24.14.1** failed with:

```text
TypeError: Cannot read properties of null (reading 'edgesOut')
```

during Vitest optional-peer resolution. Clean reinstall with **`npm install --legacy-peer-deps`** succeeded.  
`frontend/.npmrc` sets `legacy-peer-deps=true` so subsequent installs are reproducible. This is an npm client workaround, not a dependency version override.

`@testing-library/dom` was added explicitly because the clean reinstall no longer hoisted that peer automatically for `@testing-library/react`.

---

## 4. `npm audit` result

```text
found 0 vulnerabilities
```

Re-run after install. **No findings suppressed.** No remaining advisories reported for the previous five packages (`@vitest/mocker`, `tinypool`, `react-router`, `react-router-dom`, `vitest`).

---

## 5. Test / build / lint results

| Command | Result |
|---------|--------|
| `npm ls …` | Versions as in §1; `tinypool` absent |
| `npm audit` | **0 vulnerabilities** |
| `npm test` | **22 passed** / 7 files (includes original 17 WP-A tests + safePath + router smoke) |
| `npm run build` | **OK** (`tsc --noEmit` + Vite 6.4.5 production build) |
| `npm run lint` | **OK** (`tsc --noEmit`) |

Original WP-A tests were **not** removed or weakened.

---

## 6. Manual / automated regression (router & auth)

| Check | Result |
|-------|--------|
| Login route renders | Pass (`router.smoke.test.tsx`) |
| Register route renders | Pass |
| Unauthenticated `/cart` → login | Pass (CustomerRoute) |
| CUSTOMER blocked from `/admin` | Pass (AdminRoute) |
| ADMIN-only vs CUSTOMER guards | Pass (existing `guards.test.tsx`) |
| Login redirect `from` state | Only navigates when `isSafeInternalPath(from)` |
| Attacker-controlled `/\evil.com`, `//evil.com`, etc. | Rejected by `safePath` tests |
| BrowserRouter + route tree on RR7 | Build + App shell + smoke tests green |

---

## 7. Remaining vulnerabilities / limitations

| Item | Status |
|------|--------|
| npm audit findings | **None** at remediation time |
| Production Stripe / WP-B features | Unchanged / not started |
| `legacy-peer-deps` | Required on current npm 10.6 + Node 24 toolchain; revisit if npm arborist fix lands |
| Backend Phase 1–6 | Untouched |

---

## 8. Stop condition

Dependency remediation for WP-A is complete.  
**Do not start WP-B** until explicit approval.
