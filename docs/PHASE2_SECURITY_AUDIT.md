# Phase 2 Security & Code Quality Audit

**Audit date:** 2026-11-04  
**Scope:** Authentication & Authorization (Phase 2 only)  
**Mode (original):** Audit only — **no source code was modified during the audit**  
**Remediation status:** **Complete** for High F-01–F-04 and Medium F-05–F-07 — see `docs/PHASE2_SECURITY_REMEDIATION_REPORT.md` (2026-11-04)  
**Auditor stance:** Independent review against OWASP ASVS-aligned auth controls and the approved Phase 2 design  

---

## Executive summary

Phase 2 implements a solid baseline: BCrypt(12), hashed opaque refresh tokens, rotation with family reuse detection, RBAC route rules, CSRF-off for a Bearer JWT API, and integration tests that exercise the real Spring Security filter chain.

**Findings at audit time (still documented below for history):**

1. **Access tokens are not revoked on logout** and continue to authorize until expiry. → **Remediated (F-01/F-07)**
2. **JWT claims are trusted for roles and account status** without a per-request database check. → **Remediated (F-02)**
3. **Refresh rotation lacks explicit row locking**, leaving a concurrency race. → **Remediated (F-03)**
4. **No authentication rate limiting**, enabling credential stuffing / brute force at the API edge. → **Remediated (F-04)**
5. **Dev default JWT secrets** in local/standalone/compose increase misconfiguration risk. → **Remediated (F-05)**; actuator/docs locked down in prod/docker (**F-06**)

**Recommendation (updated):** High/Medium audit items above are remediated. Phase 3 remains gated on explicit user approval. Low findings (F-08–F-10, F-12–F-13, F-15) remain accepted residual risk unless prioritized later.

---

## Findings

### F-01 — Access token remains valid after logout
| | |
|---|---|
| **Severity** | **High** |
| **Status** | **Remediated** (2026-11-04) |
| **Area** | Token revocation / session termination |
| **Evidence (audit)** | `AuthService.logout` only revoked refresh tokens. Filter accepted any unexpired signed JWT. Live probe: post-logout `/auth/me` → **200**. |
| **Impact** | Stolen or retained access tokens remain usable until access TTL after logout. |
| **Fix applied** | Redis/in-memory `AccessTokenDenylist` by `jti`; filter check; logout revokes access + refresh. Fail-closed if denylist unavailable. |

### F-02 — JWT roles and user status not revalidated against database
| | |
|---|---|
| **Severity** | **High** |
| **Status** | **Remediated** (2026-11-04) |
| **Area** | JWT validation / RBAC |
| **Evidence (audit)** | Filter/JWT path trusted role claims and hard-coded `ACTIVE`. |
| **Impact** | Disabled users and demoted admins retain privileges until access token expiry. |
| **Fix applied** | Filter loads user from DB; requires `ACTIVE`; authorities from DB roles; `SUSPENDED` status added (`V3`). |

### F-03 — Concurrent refresh rotation race (no pessimistic lock)
| | |
|---|---|
| **Severity** | **High** |
| **Status** | **Remediated** (2026-11-04) |
| **Area** | Refresh token concurrency |
| **Evidence (audit)** | Rotate was read-check-write without row lock. Live probe ×8: 1×200 / 7×401 (race still code-level). |
| **Impact** | Two concurrent refreshes can issue two valid successors. |
| **Fix applied** | `findByTokenHashForUpdate` + `PESSIMISTIC_WRITE`; concurrent IT asserts single success. |

### F-04 — No rate limiting on authentication endpoints
| | |
|---|---|
| **Severity** | **High** |
| **Status** | **Remediated** (2026-11-04) |
| **Area** | Brute force / credential stuffing |
| **Evidence (audit)** | Login/register/refresh had no throttling. |
| **Impact** | Unlimited password guessing and refresh spraying. |
| **Fix applied** | `AuthRateLimiter` (Redis/in-memory); IP (+ email for login); HTTP 429; env-configurable; no permanent lockouts. |

### F-05 — Dev default JWT secrets in non-prod profiles / Compose
| | |
|---|---|
| **Severity** | **Medium** |
| **Status** | **Remediated** (2026-11-04) |
| **Area** | Secret management |
| **Evidence (audit)** | Local/standalone/compose defaults for `JWT_SECRET`. |
| **Impact** | Accidental deployment with guessable signing key. |
| **Fix applied** | `JwtSecretsValidator` fail-fast for prod/docker; Compose requires `JWT_SECRET` (no default). |

### F-06 — Public actuator metrics and OpenAPI in all environments
| | |
|---|---|
| **Severity** | **Medium** |
| **Status** | **Remediated** (2026-11-04) |
| **Area** | Information disclosure / attack surface |
| **Evidence (audit)** | Prometheus/info/OpenAPI always permitAll. |
| **Impact** | Schema/metrics exposure if internet-facing. |
| **Fix applied** | `api-docs-public` / `actuator-prometheus-public` flags; prod disables springdoc + metrics public access. |

### F-07 — Access token not bound to token version / logout epoch
| | |
|---|---|
| **Severity** | **Medium** |
| **Status** | **Remediated** (2026-11-04) with F-01 |
| **Area** | Related to F-01 |
| **Evidence (audit)** | JWT `jti` unused for revocation. |
| **Impact** | No practical way to invalidate individual access tokens before expiry. |
| **Fix applied** | `jti` required on parse; denylist key on logout. |

### F-08 — Refresh hash is unsalted SHA-256
| | |
|---|---|
| **Severity** | **Low** |
| **Area** | Refresh token storage |
| **Evidence** | `RefreshTokenService.hash` SHA-256 hex (`RefreshTokenService.java` L96–104). Raw token is 48-byte CSPRNG (`L90–93`). |
| **Impact** | Acceptable for high-entropy opaque tokens (not passwords). DB leak does not yield online-guessable secrets if entropy holds. Pepper would add defense-in-depth. |
| **Recommended fix** | Optional HMAC-SHA256 with server-side pepper from secrets manager. |

### F-09 — CSRF disabled
| | |
|---|---|
| **Severity** | **Low** (informational / accepted for Bearer API) |
| **Area** | CSRF |
| **Evidence** | `SecurityConfig.java` L38 `csrf.disable()`. Refresh/access tokens sent in JSON/`Authorization`, not cookies. |
| **Impact** | Appropriate for SPA Bearer design. Becomes Critical if refresh moves to cookies without CSRF tokens. |
| **Recommended fix** | Keep disabled for Bearer-only; if adopting HttpOnly cookie refresh, enable CSRF or SameSite strict + double-submit. |

### F-10 — CORS allow-credentials with configured origins
| | |
|---|---|
| **Severity** | **Low** |
| **Area** | CORS |
| **Evidence** | `CorsConfig.java` explicit origins, `allowCredentials(true)`, no `*`. |
| **Impact** | Sound baseline. Mis-set `CORS_ALLOWED_ORIGINS` in prod could trust hostile origins. |
| **Recommended fix** | Prod validation that origins are an allowlist; deny wildcard. |

### F-11 — Register API has no role field (privilege escalation blocked)
| | |
|---|---|
| **Severity** | **Positive control** (not a vulnerability) |
| **Evidence** | `RegisterRequest` only `email`/`password`/`fullName`. `AuthService.register` always assigns `CUSTOMER` (`AuthService.java` L57–68). Extra JSON `roles` does not grant ADMIN (live probe returned **400** when sending unexpected payload shape). |
| **Impact** | Public self-registration cannot escalate to ADMIN. |

### F-12 — Duplicate registration race relies on DB unique constraint
| | |
|---|---|
| **Severity** | **Low** |
| **Area** | Transactions |
| **Evidence** | App checks `existsByEmailIgnoreCase` then insert (`AuthService.java` L53–69). DB `uq_users_email` in `V2__auth_identity.sql` L19. |
| **Impact** | Concurrent registers may surface as 500 if `DataIntegrityViolationException` is not mapped to 409. |
| **Recommended fix** | Catch integrity violation → `EMAIL_ALREADY_EXISTS`. |

### F-13 — Sensitive data in logs
| | |
|---|---|
| **Severity** | **Low** (currently acceptable) |
| **Evidence** | Auth services do not log passwords/tokens. `GlobalExceptionHandler` logs unhandled exceptions with stack traces (`L61–65`) — ensure filters never put tokens in URLs. Local profile sets `com.ecommerce.platform: DEBUG` (`application-local.yml`). |
| **Impact** | DEBUG in shared environments could increase leakage risk from framework request logs. |
| **Recommended fix** | Keep INFO in shared envs; scrub Authorization headers in access logs. |

### F-14 — Password hashing & validation quality
| | |
|---|---|
| **Severity** | **Positive control** |
| **Evidence** | `BCryptPasswordEncoder(12)` (`SecurityConfig.java` L71–72). `@StrongPassword` enforces upper/lower/digit/special and length 8–72 (`StrongPasswordValidator.java`). |
| **Impact** | Meets common baseline; consider breached-password denylist later. |

### F-15 — Authentication error messages
| | |
|---|---|
| **Severity** | **Low** |
| **Evidence** | Login uses generic `Invalid email or password` (`AuthExceptions.java`). Register returns `EMAIL_ALREADY_EXISTS` (email enumeration). |
| **Impact** | Enumeration via register is common tradeoff; mitigate with rate limits (F-04). |

### F-16 — Integration tests do exercise Spring Security
| | |
|---|---|
| **Severity** | **Positive control** |
| **Evidence** | `AuthIntegrationTest` uses `@SpringBootTest` + `@AutoConfigureMockMvc` **without** `addFilters = false`. Assertions include unauthenticated **401** `UNAUTHORIZED`, CUSTOMER→admin **403** `ACCESS_DENIED`, ADMIN **200**, revoked refresh **401**. |
| **Impact** | Security filter chain and RBAC matchers are covered by integration tests (not mocked away). |

---

## Checklist review (requested topics)

| # | Topic | Verdict |
|---|-------|---------|
| 1 | JWT signing key security | Secret min 32 bytes enforced; HMAC via JJWT. Risk: dev defaults (F-05). |
| 2 | Access token expiry/validation | `exp` + `iss` verified; expired tested. No DB status check (F-02). |
| 3 | Refresh hashing/rotation | SHA-256 store + rotate; tested. |
| 4 | Reuse detection | Family revoke on revoked presentation; unit + IT covered. |
| 5 | Concurrent refresh | Code race (F-03); live probe 1 success / 7 fail. |
| 6 | BCrypt | Strength 12 — good. |
| 7 | Password validation | Strong policy — good. |
| 8 | RBAC | Route-level `hasRole`/`hasAnyRole` — good; JWT role lag (F-02). |
| 9 | ADMIN escalation | Self-registration cannot assign ADMIN (F-11). |
| 10 | CORS/CSRF | CORS allowlist; CSRF off (acceptable for Bearer) (F-09/F-10). |
| 11 | Auth exception handling | JSON entry point + denied handler; consistent codes. |
| 12 | Log exposure | No token/password logging found in auth path (F-13). |
| 13 | Env/secrets | Prod requires env; local/compose defaults (F-05). |
| 14 | Transaction consistency | `@Transactional` present; rotation race (F-03); register unique race (F-12). |
| 15 | API validation | Jakarta Validation on register/login/refresh — good. |

---

## Missing security test scenarios

| Missing scenario | Priority | Status after remediation |
|------------------|----------|--------------------------|
| Concurrent refresh under load (automated) | High | **Covered** — `SecurityRemediationTest` |
| Logout then access-token rejection | High | **Covered** |
| Disabled user with still-valid access JWT | High | **Covered** |
| Role removed in DB while JWT still has ADMIN | High | **Covered** (demote ADMIN→CUSTOMER) |
| Suspended / deleted user with prior JWT | High | **Covered** |
| Rate-limit behavior | Medium | **Covered** — `RateLimitTest` |
| Algorithm/`iss`/`exp` negative matrix beyond current unit tests | Medium | Partial (existing JwtServiceTest) |
| `DataIntegrityViolation` on duplicate email → 409 | Low | Open |
| CORS preflight from disallowed origin | Low | Open |

---

## Tests executed (actual results)

### Automated suite
```text
Command : mvn -B test
Date    : 2026-11-04
Result  : Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
          BUILD SUCCESS
```

Notable classes:
- `AuthIntegrationTest` (4) — full security chain
- `AuthServiceTest` (3)
- `RefreshTokenServiceTest` (3) — includes reuse → family revoke
- `JwtServiceTest` (3) — parse, tamper, expire
- `PasswordEncoderTest` (1)
- Foundation tests (remainder)

### Live probes (standalone profile, port 8087) — no code changes
| Probe | Result |
|-------|--------|
| Concurrent refresh ×8 same token | **1×200**, **7×401** |
| After logout, access token `/auth/me` | **200 (still usable)** → confirms F-01 |
| After logout, refresh token | **401** (revoked) |
| Register with extra `roles` field | **400** (no ADMIN grant) |

---

## Severity tally

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 4 (F-01, F-02, F-03, F-04) |
| Medium | 3 (F-05, F-06, F-07) |
| Low | 5 (F-08–F-10, F-12–F-13, F-15) |
| Positive controls | F-11, F-14, F-16 |

---

## Recommended remediation order

1. Access-token denylist on logout + filter check (**F-01/F-07**) — **done**
2. Pessimistic/atomic refresh rotation + concurrent test (**F-03**) — **done**
3. Per-request (or cached) user status/role verification (**F-02**) — **done**
4. Auth endpoint rate limiting (**F-04**) — **done**
5. Remove/fail on default JWT secrets outside pure local DIY (**F-05**) — **done**
6. Lock down actuator/docs in prod (**F-06**) — **done**

Full write-up: `docs/PHASE2_SECURITY_REMEDIATION_REPORT.md`.

---

## Gate

- High/Medium findings above are remediated; Low items remain open as residual risk.
- **Phase 3 must not start** until explicit user approval after remediation review.
