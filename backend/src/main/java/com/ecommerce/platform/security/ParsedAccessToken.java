package com.ecommerce.platform.security;

import java.time.Instant;
import java.util.UUID;

public record ParsedAccessToken(
        String jti,
        UUID userId,
        String email,
        Instant expiresAt
) {
}
