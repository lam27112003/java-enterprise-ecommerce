package com.ecommerce.platform.security.token;

import java.time.Duration;
import java.time.Instant;

/**
 * Stores revoked access-token JTIs until their natural JWT expiry.
 */
public interface AccessTokenDenylist {

    void revoke(String jti, Instant expiresAt);

    boolean isRevoked(String jti);

    /**
     * @return remaining TTL to keep a revoked jti; zero/negative means skip store
     */
    static Duration ttlUntil(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }
}
