# Phase 7 WP-A — Residual Remediation Report (M1 / M2)

**Date:** 2026-11-05  
**Scope:** QA findings **M1** and **M2** only (`docs/PHASE7_WP_A_FINAL_QA_REPORT.md`)  
**WP-B:** Not started  
**Dependencies:** Unchanged

---

## 1. Root causes

### M1 — AuthContext desync on terminal 401

`apiClient` cleared tokens on a final 401 but only updated React auth state when a refresh handler ran and returned `null`. If **no refresh token** existed, the client never called into AuthContext, so the UI could still appear authenticated while storage was empty and protected API calls failed.

### M2 — Encoded open-redirect paths

`isSafeInternalPath` inspected the raw string for `\` only. URI-encoded (`%5C`) and double-encoded (`%255C`) backslashes could pass, and malformed `%` sequences were not handled explicitly.

---

## 2. Fixes

### M1

- Added `setUnauthorizedHandler` / `clearSession()` in `apiClient`.
- Terminal 401 (`!skipRefresh`) and failed `refreshTokensWithBody` both call `clearSession()` (clear tokens + notify).
- `AuthProvider` registers `markAnonymous` as the unauthorized handler (alongside existing single-flight refresh handler).
- Refresh still attempted only when a refresh token exists; no extra retry loop.

### M2

- Validate raw path, then up to **two** `decodeURIComponent` passes.
- Reject `\`, `://`, protocol-relative `//`, and any form containing `%5C` / `%5c`.
- Malformed percent-encoding → `false` (no throw into login).

---

## 3. Files changed

| File | Change |
|------|--------|
| `frontend/src/shared/lib/apiClient.ts` | `clearSession`, unauthorized handler, M1 paths |
| `frontend/src/features/auth/AuthContext.tsx` | Register `markAnonymous` via `setUnauthorizedHandler` |
| `frontend/src/shared/lib/safePath.ts` | Hardened decode + encoded-backslash checks |
| `frontend/src/shared/lib/apiClient.test.ts` | M1 regression tests (+2) |
| `frontend/src/shared/lib/safePath.test.ts` | Expanded M2 cases |

---

## 4. Tests added

**M1 (`apiClient.test.ts`):**

- 401 with **no** refresh token → tokens cleared, unauthorized handler called, refresh **not** invoked, single fetch.
- 401 with refresh handler returning `null` → one refresh attempt, no retry loop, session cleared, unauthorized notified.

**M2 (`safePath.test.ts`):**

- Normal internal paths; query/fragment; protocol-relative/external; literal `\`; `%5C`; double/triple-encoded; malformed `%`; non-strings.

**Suite total:** **30 passed** (previous 22 + new cases; none removed/weakened).

---

## 5. Verification results

| Command | Result |
|---------|--------|
| `npm audit` | **0 vulnerabilities** |
| `npm test` | **30 passed** / 7 files |
| `npm run build` | **OK** |
| `npm run lint` | **OK** |

---

## 6. Remaining limitations

| Item | Notes |
|------|-------|
| L1 (QA) — `from` uses pathname only | Unchanged; optional WP-B improvement |
| L2 — `legacy-peer-deps` | Unchanged; install workaround only |
| Unauthorized handler must be registered | Works when app is under `AuthProvider` (production path). Unit tests set the handler explicitly. |
| Exotic nested encoding beyond 2 decode passes | Rejected if `%5c` remains; extremely deep encodings still treated unsafe when `%5c` appears after passes |
| Live browser E2E | Still deferred to WP-G |

---

## 7. Stop

M1 and M2 remediation complete. **Do not start WP-B** until explicit approval.
