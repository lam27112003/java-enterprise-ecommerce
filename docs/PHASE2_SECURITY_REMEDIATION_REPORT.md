# Phase 2 Security Remediation Report

**Date:** 2026-11-04  
**Scope:** Authentication / authorization / security only  
**Phase 3:** Not started  

---

## Original HIGH findings and fixes

### F-01 — Access token valid after logout
| | |
|---|---|
| **Root cause** | Logout revoked refresh tokens only; JWT filter accepted any unexpired signature. |
| **Fix** | `AccessTokenDenylist` stores JWT `jti` until access-token expiry. Redis implementation when Redis is present; in-memory fallback for test/standalone. Filter rejects denylisted JTIs. Logout revokes current access `jti` + refresh token. |
| **Redis failure** | Denylist check **fail-closed** (`503` / `DenylistUnavailableException`). Persist revoke failures throw. |

### F-02 — Stale JWT roles/status trusted
| | |
|---|---|
| **Root cause** | Filter built authorities from JWT claims and forced `ACTIVE`. |
| **Fix** | Filter loads user by `sub` from DB, requires `ACTIVE`, builds authorities from DB roles. Added `SUSPENDED` status (`V3__user_status_suspended.sql`). Role claims retained for observability only. |
| **Tests** | Disabled, suspended, demoted ADMIN, deleted user. |

### F-03 — Concurrent refresh race
| | |
|---|---|
| **Root cause** | Read-check-write without row lock. |
| **Fix** | `findByTokenHashForUpdate` with `PESSIMISTIC_WRITE` inside `@Transactional` rotate. |
| **Test** | 8 concurrent refreshes → exactly **1** success; one active successor; original reuse → 401. |

### F-04 — No auth rate limiting
| | |
|---|---|
| **Root cause** | Public login/register/refresh unbounded. |
| **Fix** | `AuthRateLimiter` (Redis counters or in-memory sliding window). IP (+ email for login). HTTP **429** `RATE_LIMIT_EXCEEDED`. Configurable via env. Redis failure **fail-open** for rate limit (availability). No permanent lockouts. |

---

## Medium findings addressed

| Finding | Fix |
|---------|-----|
| Dev JWT defaults | `JwtSecretsValidator` fails fast for `prod`/`docker` / `enforce-secure-jwt-secret`. Compose requires `JWT_SECRET` (no default). |
| Public actuator/docs | `api-docs-public` / `actuator-prometheus-public` flags; prod disables springdoc + exposes health only. |
| Unused `jti` | Required on parse; used as denylist key. |

---

## Files modified / added (security-related)

**Added:** denylist + rate-limit packages, `ParsedAccessToken`, `JwtSecretsValidator`, `ClientIpResolver`, `V3__user_status_suspended.sql`, `SecurityRemediationTest`, `RateLimitTest`, this report.

**Updated:** `JwtService`, `JwtAuthenticationFilter`, `AuthService`, `AuthController`, `RefreshTokenService`/`Repository`, `SecurityConfig`, `DatabaseUserDetailsService`, `UserStatus`, profile YAMLs, `docker-compose.yml`, docs, `TASK_TRACKER.md`.

---

## Tests executed (actual)

```text
Command : mvn -B test
Date    : 2026-11-04
Result  : Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
          BUILD SUCCESS
```

Includes real Spring Security filter chain (`@SpringBootTest` + `@AutoConfigureMockMvc` without disabling filters).

---

## Remaining risks

1. Access-token denylist / rate limit use **in-memory** when Redis autoconfig is excluded (test/standalone). Production/docker must run with Redis for distributed correctness.
2. Rate limiter fail-open on Redis outage can allow burst traffic briefly.
3. Per-request DB user/role load adds latency (acceptable for Phase 2; optional short TTL cache later with invalidation).
4. Password reset / address APIs still deferred.
5. No permanent account lockout by design (per requirements).

---

## Gate

Await approval before Phase 3 or further changes.
