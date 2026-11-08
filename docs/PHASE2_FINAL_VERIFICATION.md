# Phase 2 Final Verification (Security Remediation)

**Date:** 2026-11-04  
**Mode:** Audit / verification only — **no source code modified**  
**Phase 3:** Not started  

---

## Executive verdict

Core auth remediation controls for logout revocation, DB-backed authorization, concurrent refresh locking, refresh reuse, and rate-limit 429 responses are **implemented and covered by automated Spring Security filter-chain tests**.

**Gaps (not failures of the code paths themselves):** Redis-backed denylist/rate limiting and real PostgreSQL were **not exercised** by the default Maven suite (H2 + in-memory Redis substitutes). Production JWT-secret fail-fast and actuator lockdown are **config-correct by review** but **lack dedicated automated tests**.

---

## Checklist verification

| # | Requirement | Verdict | Evidence |
|---|-------------|---------|----------|
| 1 | Access token rejected immediately after logout | **PASS** | `SecurityRemediationTest.logoutRevokesAccessToken` + `AuthIntegrationTest` post-logout `/me` → **401** `INVALID_TOKEN`. Filter checks denylist `jti` before auth. |
| 2 | Disabled users cannot use prior JWTs | **PASS** | `disabledUserJwtIsRejected` → **403** `ACCOUNT_DISABLED`. Filter → `DatabaseUserDetailsService.loadAuthorizedUser` requires `ACTIVE`. Suspended covered similarly. |
| 3 | Demoted ADMIN loses ADMIN immediately | **PASS** | `demotedAdminLosesAdminAccessImmediately`: same JWT → `/admin/ping` **200** then after role removal **403** `ACCESS_DENIED`; `/customer/ping` still **200**. Authorities from DB roles, not JWT claims. |
| 4 | Concurrent refresh → exactly one success | **PASS** | `concurrentRefreshAllowsOnlyOneSuccess`: 8 threads via `ExecutorService.invokeAll` + MockMvc → `success == 1`, `failure == 7`. Uses `findByTokenHashForUpdate` + `PESSIMISTIC_WRITE`. |
| 5 | No duplicate active successor tokens | **PASS** | Same test asserts `countByFamilyIdAndRevokedAtIsNull(familyId) == 1`. |
| 6 | Refresh reuse detected | **PASS** | Integration: rotate then present old token → **401** (`AuthIntegrationTest`). Concurrent test re-presents original → **401**. Unit: `reuseOfRevokedTokenRevokesFamily` verifies `revokeFamily`. |
| 7 | Rate limiting returns HTTP 429 | **PASS (partial)** | `RateLimitTest.registerEndpointReturns429WhenLimitExceeded` → **429** `RATE_LIMIT_EXCEEDED`, generic message. Login/refresh limiters exist in code; **only register** has an automated 429 test. |
| 8 | Redis-backed revocation & rate limiting work | **NOT VERIFIED by suite** | Test profile **excludes** `RedisAutoConfiguration`. Beans used: `InMemoryAccessTokenDenylist`, `InMemoryAuthRateLimiter`. Redis implementations exist (`RedisAccessTokenDenylist` fail-closed; `RedisAuthRateLimiter` fail-open) but **no Testcontainers Redis test ran**. |
| 9 | Production fails safely without JWT secrets | **PASS by config review; no automated test** | `application-prod.yml` / base `${JWT_SECRET}` (no default); Compose `${JWT_SECRET:?…}`; `JwtSecretsValidator` rejects blank + known defaults when `enforce-secure-jwt-secret` / prod/docker; `JwtService` requires ≥32 bytes. **No test** boots prod profile with missing secret. |
| 10 | Sensitive Actuator endpoints protected | **PASS by config review; no automated test** | Prod: `actuator-prometheus-public: false`, exposure `include: health` only, springdoc disabled. `SecurityConfig` only permits prometheus/info when flag true. Test profile intentionally leaves docs/prometheus public. |

---

## Test execution (actual)

```text
Command : mvn -B clean test
Date    : 2026-11-04
CWD     : backend/
Result  : Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
          BUILD SUCCESS
Total time: ~01:02 min
```

### Per-class results (from Surefire)

| Class | Tests | Type |
|-------|------:|------|
| `AuthIntegrationTest` | 4 | Integration (`@SpringBootTest` + MockMvc, filters on) |
| `SecurityRemediationTest` | 6 | Integration (security remediation) |
| `RateLimitTest` | 1 | Integration (rate limit) |
| `PlatformControllerTest` | 1 | Integration (light) |
| `EcommerceApplicationTests` | 1 | Integration (context load) |
| `AuthServiceTest` | 3 | Unit (Mockito) |
| `RefreshTokenServiceTest` | 3 | Unit (Mockito) |
| `JwtServiceTest` | 3 | Unit |
| `PasswordEncoderTest` | 1 | Unit |
| `ApiResponseTest` | 2 | Unit |
| **Total** | **25** | |

**Skipped in this run:** 0  

### Excluded / not executed classes

| Class | Why not run |
|-------|-------------|
| `PostgresTestcontainersIT` | Name ends in `IT` — **outside Surefire default includes** (`*Test.java` / `*Tests.java`). Also gated by `RUN_TESTCONTAINERS=true` + Docker. Would only smoke Flyway on real Postgres; **not** auth remediation coverage. |

No Surefire `<excludes>` block in `pom.xml`; exclusion is by default naming pattern.

---

## Dependencies: PostgreSQL / Redis — real vs substitute

| Dependency | What the suite used | Notes |
|------------|---------------------|-------|
| **Database** | **H2 in-memory** `MODE=PostgreSQL` | Flyway V1–V3 applied. Not real PostgreSQL. Optional Testcontainers class present but not executed. |
| **Redis** | **Not started** | Autoconfig excluded in `application-test.yml`. In-memory denylist + rate limiter beans active. Redis/Testcontainers deps on classpath unused by default suite. |
| **Security filter chain** | **Real** | `@AutoConfigureMockMvc` without `addFilters = false`. |
| **Auth unit tests** | Mockito mocks for repositories / collaborators | Does not hit DB/Redis. |

### Concurrency test realism

`concurrentRefreshAllowsOnlyOneSuccess` uses a real fixed thread pool (`Executors.newFixedThreadPool(8)`), `invokeAll`, and parallel MockMvc `POST /api/v1/auth/refresh` against one shared refresh token. This is **actual concurrent HTTP-layer requests** in-process (not simulated sequential loops). Locking semantics were validated on **H2**, which may differ from PostgreSQL row locks under production load.

---

## Code-path notes (review)

- Logout: `AuthService.logout` denylists access `jti` + revokes refresh; filter rejects denylisted JTIs.
- Authorization: JWT parse → denylist → `loadAuthorizedUser` (existence, `ACTIVE`, DB roles).
- Refresh: transactional `PESSIMISTIC_WRITE` rotate; reuse → `revokeFamily`.
- Rate limit: IP (+ email for login); configurable env vars; register 429 tested with in-memory limiter.
- Secrets: layered fail-fast (property placeholder, validator, JwtService length).
- Actuator: prod exposure narrowed to health; matcher flags gate prometheus/docs.

---

## Remaining risks / unresolved findings

1. **Redis path untested** — distributed denylist and rate limiting never proven in CI; multi-instance correctness relies on unexecuted Redis beans.
2. **PostgreSQL concurrency untested** — H2 passed the 8-way refresh race; recommend a Docker-gated PG concurrent refresh IT before production sign-off.
3. **Rate-limit coverage gap** — login/refresh 429 not automated; only register.
4. **Prod secret / actuator** — no automated negative tests for missing `JWT_SECRET` or authenticated-only prometheus/docs.
5. **JwtSecretsValidator** does not itself enforce minimum length (JwtService does); short non-default secrets still fail at JwtService init.
6. **Rate limiter fail-open** on Redis outage can allow bursts (by design).
7. **Denylist fail-closed** on Redis outage returns errors for *all* Bearer requests while Redis is down (availability trade-off).
8. Prior Low audit items (unsalted refresh hash pepper, register email enumeration, duplicate-email integrity mapping nuances) remain out of remediation scope.

---

## Gate

Verification complete. **Do not start Phase 3** until explicit approval after this report is reviewed.
