package com.ecommerce.platform.security.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed denylist. Fail-closed on Redis errors during {@link #isRevoked(String)}
 * so a revoked token cannot slip through during an outage.
 * Registered by {@link com.ecommerce.platform.config.AuthInfrastructureConfig}.
 */
public class RedisAccessTokenDenylist implements AccessTokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(RedisAccessTokenDenylist.class);
    private static final String KEY_PREFIX = "auth:access:denylist:";

    private final StringRedisTemplate redis;

    public RedisAccessTokenDenylist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Duration ttl = AccessTokenDenylist.ttlUntil(expiresAt == null ? Instant.now().plusSeconds(60) : expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (RuntimeException ex) {
            log.error("Failed to persist access-token revocation for jti={}", jti, ex);
            throw new IllegalStateException("Unable to revoke access token", ex);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            Boolean has = redis.hasKey(KEY_PREFIX + jti);
            return Boolean.TRUE.equals(has);
        } catch (RuntimeException ex) {
            log.error("Denylist check failed; failing closed for jti={}", jti, ex);
            throw new DenylistUnavailableException("Access token revocation store unavailable", ex);
        }
    }
}
