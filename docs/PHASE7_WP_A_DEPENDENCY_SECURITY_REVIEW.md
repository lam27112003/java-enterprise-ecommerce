# Phase 7 WP-A — Frontend Dependency Security Review

**Date:** 2026-11-05  
**Scope:** `frontend/` dependency vulnerabilities discovered during WP-A QA  
**Status:** Assessment only — **no package installs or lockfile changes performed**  
**Constraint:** Do not claim audit is clean until upgrades are applied and `npm audit` is re-run

---

## 1. Verified current state

### 1.1 Declared vs resolved versions

| Package | `package.json` | Resolved (`npm ls` / lockfile) | Role |
|---------|----------------|--------------------------------|------|
| `react` | `^18.3.1` | **18.3.1** | runtime |
| `react-dom` | `^18.3.1` | **18.3.1** | runtime |
| `react-router-dom` | `^6.30.1` | **6.30.6** | runtime |
| `react-router` | (transitive via `react-router-dom`) | **6.30.6** | runtime |
| `vite` | `^6.3.5` | **6.4.5** | build / test |
| `vitest` | `^3.2.4` | **3.2.7** | dev |
| `@vitest/mocker` | (transitive) | **3.2.7** | dev |
| `tinypool` | (transitive via `vitest`) | **1.1.1** | dev |

**Fact:** `npm ls` (2026-11-05) confirms the resolved tree above. React remains 18; Vite remains 6.

### 1.2 Current dependency tree (relevant slice)

```text
ecommerce-frontend@0.1.0
├── react@18.3.1
├── react-dom@18.3.1
├── react-router-dom@6.30.6
│   └── react-router@6.30.6
├── vite@6.4.5
└── vitest@3.2.7
    ├── @vitest/mocker@3.2.7
    └── tinypool@1.1.1
```

### 1.3 `npm audit` result (verified on this machine)

```text
5 vulnerabilities (3 moderate, 2 critical)
```

| Finding | Severity | Advisory range (npm audit) | Installed | `fixAvailable` suggested by npm |
|---------|----------|----------------------------|-----------|-----------------------------------|
| `@vitest/mocker` | moderate | `<=4.1.11` | 3.2.7 | `vitest@5.1.0` (**major**, via `--force`) |
| `tinypool` | critical (×2) | `<=2.1.1` | 1.1.1 | `vitest@5.1.0` (**major**, via `--force`) |
| `react-router` | moderate (×2 advisories) | `6.0.0 – 7.17.0` | 6.30.6 | `react-router-dom@7.18.5` (**major**, via `--force`) |
| `react-router-dom` | moderate | `6.0.0-alpha.0 – 7.17.0` | 6.30.6 | same as above |
| `vitest` (parent) | critical (via deps) | `0.0.95 – 4.1.10` | 3.2.7 | `vitest@5.1.0` |

### 1.4 `npm audit fix --dry-run` (verified)

- **Does not** clear the five findings without `--force`.
- Non-force dry-run only proposes unrelated optional platform packages (rollup/esbuild/fsevents) and **still reports the same 5 vulnerabilities**.
- `npm audit fix --force` would install **`vitest@5.1.0`** and **`react-router-dom@7.18.5`** (breaking majors). **Do not run `--force` without explicit approval.**

---

## 2. Vulnerability details

### 2.1 React Router (runtime — moderate)

| ID | Summary | Affected (advisory) | First patched |
|----|---------|---------------------|---------------|
| [GHSA-wrjc-x8rr-h8h6](https://github.com/advisories/GHSA-wrjc-x8rr-h8h6) | Open redirect via backslash in `<Link>` / `useNavigate` (CVE-2025-68470 bypass) | `>=6.0.0, <7.18.0` | **7.18.0** |
| [GHSA-337j-9hxr-rhxg](https://github.com/advisories/GHSA-337j-9hxr-rhxg) | Arbitrary constructor injection via `deserializeErrors()` SSR hydration | `>=6.4.0, <7.18.0` (per registry scanners) | **7.18.0** |

**Verified facts:**

- Latest **React Router 6** on npm is **6.30.6** (same as installed).
- There is **no patched 6.x release** for GHSA-wrjc / GHSA-337j.
- npm’s only automated fix points to **`react-router-dom@7.18.5`**.

**Applicability notes (risk ranking, not “fixed” claims):**

- Open-redirect class is **relevant** to this SPA if attacker-controlled paths reach `navigate` / `<Link to>` (e.g. post-login `from` state). Defense-in-depth validation remains advisable after upgrade.
- SSR `deserializeErrors` path is **lower relevance** for this Vite SPA (no React Router SSR today), but still fails `npm audit` until ≥7.18.0.

**Related follow-on on 7.x (for target selection):**

- [GHSA-qwww-vcr4-c8h2](https://github.com/advisories/GHSA-qwww-vcr4-c8h2) (RSC CSRF): affects `>=7.12.0, <7.18.2` (and 8.x until 8.3.0). Patched on 7.x in **7.18.2**.  
- **Recommendation:** pin **`react-router-dom@7.18.5`** (not 7.18.0/7.18.1) so this advisory is also covered.  
- This app does **not** use unstable RSC APIs today; still prefer a clean audit line.

**React 18 compatibility:** `react-router-dom@7.18.5` peer is `react` / `react-dom` **`>=18`** — compatible with current React 18.3.1.  
**Avoid React Router 8** for this project: peers move toward React 19; unnecessary for clearing current findings.

### 2.2 Vitest / `@vitest/mocker` / `tinypool` (dev — moderate + critical)

| ID | Package | Summary | First patched (verified from advisories / registry) |
|----|---------|---------|------------------------------------------------------|
| [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9) | `@vitest/mocker` / `vitest` | Path traversal / arbitrary file read via redirect mock | **4.1.11** (and 5.0.0-rc.2+) |
| [GHSA-qxg6-89mg-rqv8](https://github.com/advisories/GHSA-qxg6-89mg-rqv8) | `@vitest/mocker` | Code injection via unauthenticated manual mock registration | same mocker line (npm ties to mocker `<=4.1.11`) |
| [GHSA-5gmw-xhrv-c9v3](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3) | `tinypool` | Prototype pollution → RCE via worker options | **tinypool ≥2.1.2** (critical) |
| [GHSA-85c8-ppgw-ccpr](https://github.com/advisories/GHSA-85c8-ppgw-ccpr) | `tinypool` | Prototype pollution gadget in `run()` options | **tinypool ≥2.1.2** |

**Verified facts:**

- Latest **Vitest 3** is **3.2.7** — **no patched 3.x** for GHSA-82fw (maintainers: older majors not planned to receive the fix).
- `vitest@3.2.7` depends on `tinypool@^1.1.1`. There is **no fixed tinypool 1.x**; fixed releases start at **2.1.2** (current latest observed: **2.2.0**).
- `vitest@4.1.12` depends on `@vitest/mocker@4.1.12` and **does not list `tinypool`** in its published dependencies (pool rewrite in Vitest 4).
- npm audit marks `@vitest/mocker` vulnerable for `<=4.1.11`, so prefer **`vitest@4.1.12`** (not 4.1.11) to clear the npm range with margin.
- npm’s `--force` default is **`vitest@5.1.0`**, but **4.1.12 is sufficient** per advisories and keeps a smaller jump than 5.x while retaining Vite 6 peer support (`vite: ^6 \|\| ^7 \|\| ^8`).

**Exploit context (dev-only):**

- These packages are **devDependencies** / transitive of Vitest. They are **not shipped** in the production Vite browser bundle.
- Critical tinypool issues require abusing Vitest worker option surfaces (local/CI attacker model), not end-user browsers.
- Still should be cleared for CI hygiene and Dependabot/audit gates.

---

## 3. Compatibility analysis

| Goal | Possible while clearing advisories? | Evidence |
|------|-------------------------------------|----------|
| Keep **React 18** | **Yes** | RR 7.18.5 peers `>=18`; do **not** take RR 8 |
| Keep **Vite 6** | **Yes** | Vitest 4.1.12 peers include `^6`; current Vite 6.4.5 OK |
| Keep **Vitest major 3** | **No** (for full fix) | No patched 3.x; tinypool 1.x unfixable in-range |
| Keep **React Router major 6** | **No** (for these two advisories) | No 6.x patch; need ≥7.18.0 (prefer 7.18.5) |
| Prefer Vitest 4 over 5 | **Yes** | 4.1.12 patches mocker and drops tinypool; smaller break than 5.1.0 |
| `npm audit fix` without force | **No** | Dry-run leaves all 5 findings |

### Breaking-change expectations (assumptions until verified post-upgrade)

**React Router 6 → 7 (SPA using `BrowserRouter` / `Routes` / `Navigate`):**

- `react-router-dom@7` largely re-exports `react-router`; many v6 APIs remain.
- WP-A already enables `future.v7_startTransition` / `v7_relativeSplatPath` — reduces surprise.
- **Assumption:** existing WP-A routes should mostly work; still require full Vitest + manual smoke.
- Watch for navigation batching (`startTransition`) and any relative splat path edge cases.

**Vitest 3 → 4:**

- Pool internals changed; snapshots / some matchers deprecated; config may need minor tweaks.
- **Assumption:** current suite (jsdom, `@testing-library/react`, simple mocks) likely ports with little or no code change — **must be proven** by running tests after upgrade.

---

## 4. Overrides: necessary or safe?

| Approach | Clears findings? | Safe for this repo? | Verdict |
|----------|------------------|---------------------|---------|
| `overrides: { "tinypool": "2.1.2" }` while staying on Vitest 3 | Clears **tinypool only**; **does not** fix `@vitest/mocker` | Possible interim, but runs Vitest 3 against tinypool 2 (unofficial pairing) | **Not recommended as primary** |
| Override `@vitest/mocker` alone on Vitest 3 | Unlikely compatible; mocker versions align with vitest release | High risk | **Reject** |
| Override `react-router` to 7.x under `react-router-dom@6` | Broken / unsupported | **Reject** |
| Direct upgrades (RR 7.18.5 + Vitest 4.1.12) | Expected to clear all five **after** lockfile refresh — **unverified until `npm audit` re-run** | Preferred | **Recommended** |

**Conclusion:** Direct dependency upgrades are safer than overrides. Overrides are not required if Vitest 4.1.12 and React Router DOM 7.18.5 are accepted.

---

## 5. Recommended safest upgrade strategy

### Strategy (ordered)

1. **Accept major bumps that have no same-major patch** (proven above):
   - `react-router-dom`: **6.30.6 → 7.18.5** (keeps React 18).
   - `vitest`: **3.2.7 → 4.1.12** (keeps Vite 6; clears mocker + removes tinypool dependency).
2. **Do not** jump to Vitest 5 or React Router 8 unless Vitest 4 / RR 7.18.5 fail audit or peers.
3. **Do not** use `npm audit fix --force` as the primary mechanism (opaque; prefers Vitest 5).
4. After install, **verify** with `npm ls` + `npm audit` before claiming fixed.
5. Run regression tests (§7). Fix only compile/test breakages required by the upgrades (separate implementation approval).

### Why not stay on Router 6 / Vitest 3

- **Verified:** no compatible patched releases exist on those majors for the open advisories.
- Per project rules: majors may be upgraded **only when** no compatible patched alternative exists — **that condition is met**.

---

## 6. Exact commands (execute only after approval)

Working directory: `frontend/`

```bash
# 1) Apply targeted upgrades (prefer exact pins for reproducibility)
npm install react-router-dom@7.18.5
npm install -D vitest@4.1.12

# 2) Confirm resolved versions
npm ls react react-dom react-router react-router-dom vite vitest @vitest/mocker tinypool

# 3) Re-audit (SUCCESS CRITERION: 0 vulnerabilities, or only documented residual unrelated findings)
npm audit

# 4) Regression
npm test
npm run build
npm run lint
```

Optional pin style in `package.json` (implementation step):

```json
"react-router-dom": "7.18.5",
"vitest": "4.1.12"
```

(or `^7.18.5` / `^4.1.12` if the team prefers caret ranges after verifying lockfile).

**Forbidden unless separately approved:** `npm audit fix --force`.

---

## 7. Regression test plan (required after upgrade implementation)

| Check | Pass criteria |
|-------|---------------|
| `npm ls` | `react-router` / `react-router-dom` ≥ **7.18.5**; `vitest` / `@vitest/mocker` ≥ **4.1.12**; `tinypool` absent **or** ≥ **2.1.2** if still present |
| `npm audit` | **0** findings for the five reported packages (ideally total 0) |
| `npm test` | All existing WP-A Vitest suites green (apiClient, guards, tokenStorage, env, App shell) — **do not weaken tests** |
| `npm run build` | `tsc --noEmit` + Vite production build succeed |
| Manual smoke | `/`, `/login`, `/register`, guarded `/cart` redirect, `/admin` unauthorized path |
| Router-specific | Login redirect `from` state; no open-redirect with `/\evil.example` style paths if tested |
| Docs/tracker | Note versions actually resolved after install |

Backend Phase 1–6 suites are **out of scope** for this frontend-only change (no backend impact expected).

---

## 8. Remaining risks / assumptions

| Item | Type | Notes |
|------|------|-------|
| Post-upgrade `npm audit` clean | **Unverified** until commands in §6 run | Do not claim fixed yet |
| Vitest 4 config/API tweaks | **Assumption** | May need small config updates; unknown until `npm test` |
| React Router 7 behavioral diffs | **Assumption** | Low for current WP-A surface; still smoke-test |
| GHSA-337j SSR | **Lower runtime risk** for SPA | Still drives audit until RR ≥7.18.0 |
| Dev-only Vitest/tinypool | **Fact** | Not in production bundle; still block CI/audit policy |
| Future RR 8 / React 19 | **Out of scope** | Not required to clear current findings |

---

## 9. Decision summary for approver

| Decision | Recommendation |
|----------|----------------|
| Keep React 18 + Vite 6 | **Yes** |
| Stay on React Router 6 | **Impossible** for GHSA-wrjc / GHSA-337j |
| Stay on Vitest 3 | **Impossible** for mocker + tinypool without incomplete overrides |
| Target versions | **`react-router-dom@7.18.5`**, **`vitest@4.1.12`** |
| Use npm overrides | **No** (prefer direct upgrades) |
| Use `npm audit fix --force` | **No** |
| Implement now | **No** — waiting for explicit approval |
| Start WP-B | **No** |

---

## 10. Assessment provenance

Commands run (read-only / dry-run) on 2026-11-05 in `frontend/`:

- `npm audit`
- `npm audit fix --dry-run`
- `npm ls vitest @vitest/mocker tinypool react-router react-router-dom vite react react-dom --all`
- `npm view` for `react-router@6`, `react-router-dom@7.18.5`, `vitest@3|4|5`, `tinypool@2`, peerDependencies

No `package.json` / `package-lock.json` modifications were made for this assessment.
