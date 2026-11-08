package com.ecommerce.platform.security.token;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback denylist when Redis is not configured (test/standalone).
 * Registered by {@link com.ecommerce.platform.config.AuthInfrastructureConfig}.
 */
public class InMemoryAccessTokenDenylist implements AccessTokenDenylist {

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Instant exp = expiresAt == null ? Instant.now().plusSeconds(60) : expiresAt;
        if (exp.isAfter(Instant.now())) {
            revokedUntil.put(jti, exp);
        }
        purgeExpired();
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Instant until = revokedUntil.get(jti);
        if (until == null) {
            return false;
        }
        if (until.isBefore(Instant.now())) {
            revokedUntil.remove(jti);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedUntil.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
