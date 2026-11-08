# Phase 2 Integration Verification

**Date:** 2026-11-04  
**Scope:** Redis / PostgreSQL Testcontainers ITs, rate-limit + production-config tests  
**Phase 3:** Not started  

---

## Executive verdict

**Integration verification is COMPLETE.**

| Suite | Command | Result |
|-------|---------|--------|
| Surefire | `mvn -B clean test` | **PASS** — Tests run: **33**, Failures: 0, Errors: 0, Skipped: 0 |
| Failsafe | `mvn -B failsafe:integration-test failsafe:verify` | **PASS** — Tests run: **10**, Failures: 0, Errors: 0, Skipped: 0 |

Real Docker Engine was used. No IT was skipped, mocked, or bypassed.

---

## Environment verification (pre-run)

| Check | Result |
|-------|--------|
| Docker CLI | **29.9.2** (`docker version`) |
| Docker Engine | **29.9.2** / Docker Desktop 4.98.0 |
| Compose | v5.6.1 (host-verified earlier) |
| `docker run --rm hello-world` | **PASS** |
| Testcontainers detection | **PASS** — containers started (`postgres:16-alpine`, `redis:7.2-alpine`) |
| JDK for Maven | Temurin **21.0.13** (`JAVA_HOME`) |

---

## Real infrastructure used

| Layer | Surefire (`*Test`) | Failsafe (`*IT`) |
|-------|--------------------|------------------|
| Database | H2 in-memory `MODE=PostgreSQL` | **PostgreSQL 16 Alpine** (Testcontainers) |
| Redis | Excluded → in-memory denylist/rate limiter | **Redis 7.2 Alpine** (Testcontainers `GenericContainer`) |
| Docker | Not required | **Required and available** |

### Versions

| Component | Version / image |
|-----------|-----------------|
| Testcontainers (runtime) | **1.21.4** |
| maven-failsafe-plugin | **3.5.6** |
| `com.redis:testcontainers-redis` (classpath) | **2.2.2** |
| PostgreSQL image | `postgres:16-alpine` |
| Redis image | `redis:7.2-alpine` |

---

## Failsafe results (executed)

### `PostgresAuthIT` — 4/4 PASS

| Test | Verified |
|------|----------|
| `flywayMigrationsAppliedOnRealPostgres` | Flyway history + seed on real PG |
| `authenticationRepositoryOperationsPersist` | User/roles + refresh token persist |
| `concurrentRefreshRotationUsesRowLocking` | 8 concurrent refreshes → **1** success; one active successor; reuse → 401 |
| `transactionRollbackDoesNotPersistUser` | Rollback leaves no user row |

### `RedisSecurityIT` — 6/6 PASS

| Test | Verified |
|------|----------|
| `logoutStoresRevocationInRedisAndRejectsToken` | `jti` keys in Redis; `/me` → 401 |
| `denylistSurvivesClientRestartWhileRedisPreserved` | New client still sees revocation |
| `denylistTtlMatchesAccessTokenExpiry` | TTL ≈ JWT remaining life; key expires |
| `rateLimitingWorksThroughRedis` | Register via Redis counters → HTTP 429 |
| `multipleInstancesShareRateLimitsViaRedis` | Two limiter instances share Redis counters |
| `redisOutageFailsClosedForDenylistAndOpenForRateLimit` | Denylist → `DenylistUnavailableException`; rate limit fail-open |

---

## Surefire coverage (executed)

Includes expanded rate-limit tests (login / register / refresh / window recovery), production JWT negatives, actuator lockdown, remediation + auth suites — **33** total.

---

## Fix applied during this verification run

**Root cause:** `@ConditionalOnBean(RedisConnectionFactory)` on component-scanned / early `@Configuration` beans evaluated before Redis auto-configuration, so `InMemoryAccessTokenDenylist` won even when Redis was present.

**Fix:** `AuthInfrastructureConfig` selects Redis vs in-memory implementations at bean-creation time via `ObjectProvider<RedisConnectionFactory>` / `ObjectProvider<StringRedisTemplate>`.

Also fixed flaky `RateLimitTest` client-key collisions by using unique `X-Forwarded-For` values.

---

## Documented Redis failure behavior (verified)

| Operation | Behavior |
|-----------|----------|
| Denylist `isRevoked` when Redis down | **Fail-closed** → `DenylistUnavailableException` |
| Denylist `revoke` when Redis down | Throws `IllegalStateException` |
| Rate limiter when Redis down | **Fail-open** (request allowed) |

---

## Remaining risks

1. Failsafe images must be pullable in CI (first run may be slower).
2. Redis outage test stops/restarts the Redis container; Spring’s primary Redis client may log timeouts during that test (expected).
3. Low audit items from Phase 2 (pepper, register enumeration, etc.) remain out of scope.

---

## Gate

Integration verification **passed**. Phase 3 remains **blocked** until explicit user approval.
